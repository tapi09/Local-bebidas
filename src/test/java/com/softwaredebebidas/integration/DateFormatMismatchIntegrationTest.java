package com.softwaredebebidas.integration;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.presenter.HomePresenter;
import com.softwaredebebidas.presenter.ReportPresenter;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.service.CsvService;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.service.ReceiptService;
import com.softwaredebebidas.service.ReportService;
import com.softwaredebebidas.service.SalesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression suite for the ReportPresenter / HomePresenter date-format bug (auditoria v3, B1).
 * ReportPresenter and HomePresenter used to build fromDate/toDate/"today" as dd/MM/yyyy and hand
 * it straight to SaleRepository, which compares it as a raw string against sale_date — a column
 * that SalesService stamps in ISO (yyyy-MM-dd). These tests wire up the REAL presenter, service,
 * and repository objects (no mocks) against an in-memory DB, exactly like the running app, and
 * create sales through the real SalesService flow so sale_date is stamped the way it is in
 * production. Fixed: HomePresenter/ReportPresenter now convert to ISO at the repository boundary.
 * These tests are the regression gate that keeps that fix in place.
 */
class DateFormatMismatchIntegrationTest {

    private DatabaseManager dbManager;
    private ProductRepository productRepository;
    private SaleRepository saleRepository;
    private StockMovementRepository stockMovementRepository;
    private SalesService salesService;
    private ReportService reportService;
    private ReportPresenter reportPresenter;
    private HomePresenter homePresenter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        productRepository = new ProductRepository(dbManager);
        saleRepository = new SaleRepository(dbManager);
        stockMovementRepository = new StockMovementRepository(dbManager);
        InventoryService inventoryService = new InventoryService(stockMovementRepository, productRepository);
        ReceiptService receiptService = new ReceiptService(productRepository);
        salesService = new SalesService(saleRepository, stockMovementRepository, productRepository, inventoryService, receiptService, dbManager);
        reportService = new ReportService(productRepository, saleRepository, stockMovementRepository, dbManager);
        reportPresenter = new ReportPresenter(reportService, new CsvService(tempDir));
        homePresenter = new HomePresenter(saleRepository, productRepository);
    }

    private Long createProductWithStock(int stock) throws SQLException {
        Product product = new Product();
        product.setName("Coca-Cola 500ml");
        product.setCategory("Gaseosas");
        product.setPresentation("Botella 500ml");
        product.setCostPrice(300.0);
        product.setSalePrice(600.0);
        product.setBarcode("77900099");
        product.setMinStock(5);
        Long productId = productRepository.save(product);

        com.softwaredebebidas.model.StockMovement entry = new com.softwaredebebidas.model.StockMovement();
        entry.setProductId(productId);
        entry.setMovementType("ENTRY");
        entry.setQuantity(stock);
        entry.setReferenceType("PURCHASE");
        entry.setNotes("Stock inicial");
        stockMovementRepository.insert(entry);
        productRepository.updateStock(dbManager.getConnection(), productId, stock);
        return productId;
    }

    @Test
    void reportPresenterHidesSaleMadeTodayThroughRealFlow() throws SQLException {
        Long productId = createProductWithStock(50);

        // Real production flow: SalesService stamps sale_date as ISO "today" automatically.
        Sale sale = new Sale();
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(3);
        item.setUnitPrice(600.0);
        boolean created = salesService.createSale(sale, Collections.singletonList(item));
        assertThat(created).isTrue();

        // Real production flow: the Reports screen preset button builds dd/MM/yyyy.
        reportPresenter.setPresetDateRange("TODAY");

        List<ReportService.SalesPeriodReport> report = reportPresenter.generateSalesByPeriodReport();

        // BUG: the sale exists (created=true above) but the report comes back empty because
        // fromDate/toDate ("dd/MM/yyyy") never match sale_date ("yyyy-MM-dd") in the BETWEEN.
        assertThat(report)
                .as("La venta recién creada debería aparecer en el reporte de ventas por período de hoy")
                .hasSize(1);
        assertThat(report.get(0).getTotalRevenue()).isCloseTo(1800.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void homePresenterTodaySalesIsWrongThroughRealFlow() throws SQLException {
        Long productId = createProductWithStock(50);

        Sale sale = new Sale();
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(2);
        item.setUnitPrice(600.0);
        boolean created = salesService.createSale(sale, Collections.singletonList(item));
        assertThat(created).isTrue();

        double todaySales = homePresenter.getTodaySales();

        // BUG: dashboard shows 0 instead of 1200.0 because HomePresenter asks
        // SaleRepository.sumSalesForDate() with a dd/MM/yyyy string against an ISO column.
        assertThat(todaySales)
                .as("El dashboard debería mostrar el total real de ventas de hoy")
                .isCloseTo(1200.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void reportPresenterRejectsInvertedDateRange() {
        // An inverted range should fail loudly instead of silently returning an empty report,
        // so the cashier isn't misled into thinking there were no sales in the period.
        reportPresenter.setDateRange("20/09/2026", "01/09/2026");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reportPresenter.generateSalesByPeriodReport())
                .as("Un rango de fechas invertido (desde > hasta) debería lanzar un error claro")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportPresenterRejectsImpossibleDateThroughRealFlow() {
        // "31/13/2026" is not a valid calendar date at all (month 13). DateUtils.parse wraps the
        // underlying DateTimeParseException in an IllegalArgumentException — this test confirms
        // that wrapping survives intact all the way through the presenter, not just at the
        // DateUtils unit-test level.
        reportPresenter.setDateRange("31/13/2026", "01/01/2026");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reportPresenter.generateSalesByPeriodReport())
                .as("Una fecha calendáricamente imposible debería lanzar un error claro, no una excepción interna")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void homePresenterTodaySalesIsZeroWhenNoSalesToday() throws SQLException {
        createProductWithStock(50);
        // No sale created — the dashboard must show a clean 0.0, not an error and not a stale
        // total left over from a previous day's query.
        assertThat(homePresenter.getTodaySales())
                .as("Sin ventas hoy, el dashboard debe mostrar 0.0")
                .isEqualTo(0.0);
    }

    @Test
    void homePresenterAndReportPresenterIsolateTodayFromOtherDays() throws SQLException {
        Long productId = createProductWithStock(50);

        // A sale explicitly backdated to yesterday — SalesService only auto-stamps sale_date
        // when it is not already set, so this exercises the real production insert path.
        Sale yesterdaySale = new Sale();
        yesterdaySale.setChannel("IN");
        yesterdaySale.setPaymentMethod("CASH");
        yesterdaySale.setSaleDate(LocalDate.now().minusDays(1).toString());
        SaleItem yesterdayItem = new SaleItem();
        yesterdayItem.setProductId(productId);
        yesterdayItem.setQuantity(5);
        yesterdayItem.setUnitPrice(600.0);
        assertThat(salesService.createSale(yesterdaySale, Collections.singletonList(yesterdayItem))).isTrue();

        // A sale made today (no explicit sale_date — stamped automatically as "today").
        Sale todaySale = new Sale();
        todaySale.setChannel("IN");
        todaySale.setPaymentMethod("CASH");
        SaleItem todayItem = new SaleItem();
        todayItem.setProductId(productId);
        todayItem.setQuantity(2);
        todayItem.setUnitPrice(600.0);
        assertThat(salesService.createSale(todaySale, Collections.singletonList(todayItem))).isTrue();

        assertThat(homePresenter.getTodaySales())
                .as("El dashboard no debe mezclar ventas de ayer con las de hoy")
                .isCloseTo(1200.0, org.assertj.core.data.Offset.offset(0.01));

        reportPresenter.setPresetDateRange("TODAY");
        List<ReportService.SalesPeriodReport> report = reportPresenter.generateSalesByPeriodReport();

        assertThat(report)
                .as("El reporte de 'hoy' no debe incluir la venta de ayer")
                .hasSize(1);
        assertThat(report.get(0).getTotalRevenue())
                .isCloseTo(1200.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
