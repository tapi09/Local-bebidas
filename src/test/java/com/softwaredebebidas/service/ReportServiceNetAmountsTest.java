package com.softwaredebebidas.service;

import com.softwaredebebidas.model.DailySalesDetailReport;
import com.softwaredebebidas.model.DailySalesDetailRow;
import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * The detail and top-sellers reports must use NET amounts (sale-level discount
 * allocated proportionally) so they agree with the sales-by-period report.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceNetAmountsTest {

    private static final double EPS = 0.0001;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private SaleRepository saleRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private DatabaseManager dbManager;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(productRepository, saleRepository, stockMovementRepository, dbManager);
    }

    @Test
    @DisplayName("detalle diario: el total general coincide con ventas por período (descuentos, multi-producto y anuladas)")
    void detailGrandTotalMatchesSalesByPeriod() throws SQLException {
        // A: 2 products, gross 2550, 20% off -> net 2040
        Sale saleA = sale(1L, "2026-08-03", "ACTIVE", 2040.0);
        // B: single product, no discount -> net 600
        Sale saleB = sale(2L, "2026-08-03", "ACTIVE", 600.0);
        // C: cancelled, gross 1000, fixed 100 off -> net 900
        Sale saleC = sale(3L, "2026-08-03", "CANCELLED", 900.0);

        List<SaleItem> items = Arrays.asList(
                item(1L, 1L, 1L, 2, 600.0), item(2L, 1L, 2L, 3, 450.0),
                item(3L, 2L, 1L, 1, 600.0),
                item(4L, 3L, 2L, 2, 500.0));

        when(saleRepository.findAllByDateRange("2026-08-01", "2026-08-05")).thenReturn(Arrays.asList(saleA, saleB, saleC));
        when(saleRepository.findByDateRange("2026-08-01", "2026-08-05")).thenReturn(Arrays.asList(saleA, saleB));
        when(saleRepository.findItemsBySaleIds(Arrays.asList(1L, 2L, 3L))).thenReturn(items);
        when(productRepository.findAllByIds(anyList()))
                .thenReturn(Arrays.asList(product(1L, "Coca"), product(2L, "Sprite")));

        DailySalesDetailReport detail = reportService.getDailySalesDetailReport("2026-08-01", "2026-08-05");
        double periodTotal = reportService.getSalesByPeriodReport("2026-08-01", "2026-08-05").stream()
                .mapToDouble(ReportService.SalesPeriodReport::getTotalRevenue).sum();

        assertThat(periodTotal).isCloseTo(2640.0, offset(EPS));
        assertThat(detail.getGrandTotal()).isCloseTo(periodTotal, offset(EPS));

        // The cancelled sale shows its NET original line and the negated cancellation.
        DailySalesDetailRow original = row(detail, "Sprite (Venta anulada)");
        DailySalesDetailRow cancellation = row(detail, "Sprite (Anulación)");
        assertThat(original.getLineTotal()).isCloseTo(900.0, offset(EPS));
        assertThat(cancellation.getLineTotal()).isCloseTo(-900.0, offset(EPS));
        assertThat(original.getQuantity()).isEqualTo(2);
        assertThat(cancellation.getQuantity()).isEqualTo(-2);
        assertThat(original.getUnitPrice()).isCloseTo(450.0, offset(EPS));

        // Discounted multi-product sale: Coca 2 x 600 = 1200 gross -> 960 net (+ 600 from sale B).
        DailySalesDetailRow coca = row(detail, "Coca");
        assertThat(coca.getQuantity()).isEqualTo(3);
        assertThat(coca.getLineTotal()).isCloseTo(960.0 + 600.0, offset(EPS));
        assertThat(coca.getUnitPrice()).isCloseTo((960.0 + 600.0) / 3, offset(EPS));
    }

    @Test
    @DisplayName("más vendidos: el total por producto es neto del descuento de la venta")
    void topSellersUseNetAmounts() throws SQLException {
        Sale saleA = sale(1L, "2026-08-03", "ACTIVE", 2040.0); // gross 2550, net 2040
        Sale saleB = sale(2L, "2026-08-03", "ACTIVE", 600.0);  // no discount

        when(saleRepository.findAllActive()).thenReturn(Arrays.asList(saleA, saleB));
        when(saleRepository.findItemsBySaleIds(Arrays.asList(1L, 2L))).thenReturn(Arrays.asList(
                item(1L, 1L, 1L, 2, 600.0), item(2L, 1L, 2L, 3, 450.0),
                item(3L, 2L, 1L, 1, 600.0)));
        when(productRepository.findAllByIds(anyList()))
                .thenReturn(Arrays.asList(product(1L, "Coca"), product(2L, "Sprite")));

        List<ReportService.TopSellerReport> top = reportService.getTopSellersReport(10);

        assertThat(top).hasSize(2);
        // Coca: qty 3, net 960 + 600; Sprite: qty 3, net 1080. Tie on quantity -> lower product id first.
        assertThat(top.get(0).getProductName()).isEqualTo("Coca");
        assertThat(top.get(0).getTotalQuantity()).isEqualTo(3);
        assertThat(top.get(0).getTotalSales()).isCloseTo(1560.0, offset(EPS));
        assertThat(top.get(1).getProductName()).isEqualTo("Sprite");
        assertThat(top.get(1).getTotalSales()).isCloseTo(1080.0, offset(EPS));
    }

    @Test
    @DisplayName("más vendidos: ordena por cantidad descendente y respeta el límite")
    void topSellersOrderByQuantityAndApplyLimit() throws SQLException {
        Sale sale = sale(1L, "2026-08-03", "ACTIVE", 1000.0);
        when(saleRepository.findAllActive()).thenReturn(List.of(sale));
        when(saleRepository.findItemsBySaleIds(List.of(1L))).thenReturn(Arrays.asList(
                item(1L, 1L, 1L, 1, 100.0), item(2L, 1L, 2L, 5, 100.0), item(3L, 1L, 3L, 4, 100.0)));
        when(productRepository.findAllByIds(anyList()))
                .thenReturn(Arrays.asList(product(1L, "A"), product(2L, "B"), product(3L, "C")));

        List<ReportService.TopSellerReport> top = reportService.getTopSellersReport(2);

        assertThat(top).extracting(ReportService.TopSellerReport::getProductName).containsExactly("B", "C");
        assertThat(top.get(0).getTotalSales()).isCloseTo(500.0, offset(EPS));
    }

    @Test
    @DisplayName("más vendidos: sin ventas activas devuelve lista vacía")
    void topSellersEmptyWhenNoActiveSales() throws SQLException {
        when(saleRepository.findAllActive()).thenReturn(List.of());

        assertThat(reportService.getTopSellersReport(5)).isEmpty();
    }

    @Test
    @DisplayName("más vendidos: un error de base de datos se propaga como RuntimeException")
    void topSellersWrapsSqlException() throws SQLException {
        when(saleRepository.findAllActive()).thenThrow(new SQLException("boom"));

        assertThatThrownBy(() -> reportService.getTopSellersReport(5)).isInstanceOf(RuntimeException.class);
    }

    private static DailySalesDetailRow row(DailySalesDetailReport report, String name) {
        return report.getRows().stream()
                .filter(r -> name.equals(r.getProductName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing row: " + name));
    }

    private static Sale sale(Long id, String date, String status, double netTotal) {
        Sale sale = new Sale();
        sale.setId(id);
        sale.setSaleDate(date);
        sale.setChannel("IN");
        sale.setStatus(status);
        sale.setTotalAmount(netTotal);
        return sale;
    }

    private static SaleItem item(Long id, Long saleId, Long productId, int quantity, double unitPrice) {
        SaleItem item = new SaleItem();
        item.setId(id);
        item.setSaleId(saleId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setSubtotal(quantity * unitPrice);
        return item;
    }

    private static Product product(Long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        return p;
    }
}
