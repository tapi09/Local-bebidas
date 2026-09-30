package com.softwaredebebidas.service;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.model.StockMovement;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private DatabaseManager dbManager;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(productRepository, saleRepository, stockMovementRepository, dbManager);
    }

    // ========================================
    // Margin Per Product Report
    // ========================================

    @Test
    void marginReportSortsByMarginPercentDescending() throws SQLException {
        Product a = createProduct(1L, "Coca-Cola", "Gaseosas", 300.0, 600.0);
        Product b = createProduct(2L, "Pepsi", "Gaseosas", 500.0, 650.0);
        Product c = createProduct(3L, "Agua", "Aguas", 200.0, 250.0);
        when(productRepository.findAllActive()).thenReturn(Arrays.asList(a, b, c));

        List<ReportService.MarginReport> result = reportService.getMarginReport();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getProductName()).isEqualTo("Coca-Cola");
        assertThat(result.get(0).getMarginPercent()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.get(1).getProductName()).isEqualTo("Pepsi");
        assertThat(result.get(1).getMarginPercent()).isCloseTo(30.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.get(2).getProductName()).isEqualTo("Agua");
        assertThat(result.get(2).getMarginPercent()).isCloseTo(25.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void marginReportCalculatesMarginCorrectly() throws SQLException {
        Product product = createProduct(1L, "Cerveza", "Birras", 200.0, 350.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        List<ReportService.MarginReport> result = reportService.getMarginReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMargin()).isCloseTo(150.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.get(0).getMarginPercent()).isCloseTo(75.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void marginReportFlagsZeroCostProducts() throws SQLException {
        Product freeProduct = createProduct(1L, "Muestra", "Promo", 0.0, 0.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(freeProduct));

        List<ReportService.MarginReport> result = reportService.getMarginReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMarginPercent()).isEqualTo(0.0);
        assertThat(result.get(0).isZeroCost()).isTrue();
    }

    @Test
    void marginReportIncludesCategoryAndPrices() throws SQLException {
        Product product = createProduct(1L, "Fernet", "Birras", 800.0, 1500.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        List<ReportService.MarginReport> result = reportService.getMarginReport();

        assertThat(result.get(0).getCategory()).isEqualTo("Birras");
        assertThat(result.get(0).getCostPrice()).isCloseTo(800.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.get(0).getSalePrice()).isCloseTo(1500.0, org.assertj.core.data.Offset.offset(0.01));
    }

    // ========================================
    // Sales by Period Report
    // ========================================

    @Test
    void salesByPeriodCalculatesRevenueAndCount() throws SQLException {
        Sale sale1 = createSale("2026-07-01", "IN", 5000.0);
        Sale sale2 = createSale("2026-07-01", "IN", 3200.0);
        Sale sale3 = createSale("2026-07-01", "PEDIDOSYA", 1800.0);
        when(saleRepository.findByDateRange("01/07/2026", "01/07/2026")).thenReturn(Arrays.asList(sale1, sale2, sale3));

        List<ReportService.SalesPeriodReport> result = reportService.getSalesByPeriodReport("01/07/2026", "01/07/2026");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalRevenue()).isCloseTo(10000.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.get(0).getTransactionCount()).isEqualTo(3);
        assertThat(result.get(0).getAverageTicket()).isCloseTo(3333.33, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void salesByPeriodGroupsByDay() throws SQLException {
        Sale day1 = createSale("2026-07-01", "IN", 5000.0);
        Sale day2 = createSale("2026-07-02", "IN", 7000.0);
        when(saleRepository.findByDateRange("01/07/2026", "02/07/2026")).thenReturn(Arrays.asList(day1, day2));

        List<ReportService.SalesPeriodReport> result = reportService.getSalesByPeriodReport("01/07/2026", "02/07/2026");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPeriod()).isEqualTo("01/07/2026");
        assertThat(result.get(0).getTotalRevenue()).isCloseTo(5000.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.get(1).getPeriod()).isEqualTo("02/07/2026");
        assertThat(result.get(1).getTotalRevenue()).isCloseTo(7000.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void salesByPeriodReturnsEmptyForNoSales() throws SQLException {
        when(saleRepository.findByDateRange("01/07/2026", "01/07/2026")).thenReturn(Collections.emptyList());

        List<ReportService.SalesPeriodReport> result = reportService.getSalesByPeriodReport("01/07/2026", "01/07/2026");

        assertThat(result).isEmpty();
    }

    @Test
    void salesByPeriodFiltersOutOfRangeSales() throws SQLException {
        Sale inRange = createSale("2026-07-01", "IN", 5000.0);
        when(saleRepository.findByDateRange("01/07/2026", "01/07/2026")).thenReturn(Collections.singletonList(inRange));

        List<ReportService.SalesPeriodReport> result = reportService.getSalesByPeriodReport("01/07/2026", "01/07/2026");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalRevenue()).isCloseTo(5000.0, org.assertj.core.data.Offset.offset(0.01));
    }

    // ========================================
    // Channel Comparison Report
    // ========================================

    @Test
    void channelComparisonShowsRevenuePerChannel() throws SQLException {
        Sale local1 = createSale("2026-07-01", "IN", 15000.0);
        Sale local2 = createSale("2026-07-01", "IN", 15000.0);
        Sale pedidosya = createSale("2026-07-01", "PEDIDOSYA", 10000.0);
        when(saleRepository.findByDateRange("01/07/2026", "01/07/2026")).thenReturn(Arrays.asList(local1, local2, pedidosya));

        List<ReportService.ChannelReport> result = reportService.getChannelComparisonReport("01/07/2026", "01/07/2026");

        assertThat(result).hasSize(2);
        ReportService.ChannelReport local = result.stream()
                .filter(c -> "IN".equals(c.getChannel())).findFirst().orElse(null);
        ReportService.ChannelReport py = result.stream()
                .filter(c -> "PEDIDOSYA".equals(c.getChannel())).findFirst().orElse(null);

        assertThat(local).isNotNull();
        assertThat(local.getRevenue()).isCloseTo(30000.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(local.getTransactionCount()).isEqualTo(2);
        assertThat(local.getPercentage()).isCloseTo(75.0, org.assertj.core.data.Offset.offset(0.01));

        assertThat(py).isNotNull();
        assertThat(py.getRevenue()).isCloseTo(10000.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(py.getTransactionCount()).isEqualTo(1);
        assertThat(py.getPercentage()).isCloseTo(25.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void channelComparisonSingleChannelShows100Percent() throws SQLException {
        Sale local = createSale("2026-07-01", "IN", 20000.0);
        when(saleRepository.findByDateRange("01/07/2026", "01/07/2026")).thenReturn(Collections.singletonList(local));

        List<ReportService.ChannelReport> result = reportService.getChannelComparisonReport("01/07/2026", "01/07/2026");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChannel()).isEqualTo("IN");
        assertThat(result.get(0).getPercentage()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void channelComparisonReturnsEmptyForNoSales() throws SQLException {
        when(saleRepository.findByDateRange("01/07/2026", "01/07/2026")).thenReturn(Collections.emptyList());

        List<ReportService.ChannelReport> result = reportService.getChannelComparisonReport("01/07/2026", "01/07/2026");

        assertThat(result).isEmpty();
    }

    // ========================================
    // Stock Rotation Report
    // ========================================

    @Test
    void rotationReportCalculatesTurnover() throws SQLException {
        Product p = createProduct(1L, "Coca-Cola 500ml", "Gaseosas", 300.0, 600.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(p));

        // 150 units entered, 120 units sold → closing stock = 30, opening = 0, average = 15
        StockMovement entry1 = createMovement(1L, "ENTRY", 100);
        StockMovement entry2 = createMovement(1L, "ENTRY", 50);
        StockMovement exit1 = createMovement(1L, "EXIT", 80);
        StockMovement exit2 = createMovement(1L, "EXIT", 40);
        when(stockMovementRepository.findByProductIds(Collections.singletonList(1L)))
                .thenReturn(Arrays.asList(entry1, entry2, exit1, exit2));
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 30)); // 150 - 120

        List<ReportService.RotationReport> result = reportService.getRotationReport("2026-07-01", "2026-07-31");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUnitsSold()).isEqualTo(120);
        // opening = 30 - 30 = 0, average = (0 + 30) / 2 = 15
        assertThat(result.get(0).getRotation()).isCloseTo(8.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void rotationReportZeroSalesShowsZeroRotation() throws SQLException {
        Product p = createProduct(1L, "Agua", "Aguas", 200.0, 250.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(p));
        when(stockMovementRepository.findByProductIds(Collections.singletonList(1L))).thenReturn(Collections.emptyList());
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L))).thenReturn(Collections.emptyMap());

        List<ReportService.RotationReport> result = reportService.getRotationReport("2026-07-01", "2026-07-31");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUnitsSold()).isEqualTo(0);
        assertThat(result.get(0).getRotation()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void rotationReportIncludesProductNameAndCategory() throws SQLException {
        Product p = createProduct(1L, "Fernet", "Birras", 800.0, 1500.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(p));
        when(stockMovementRepository.findByProductIds(Collections.singletonList(1L))).thenReturn(Collections.emptyList());
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L))).thenReturn(Collections.emptyMap());

        List<ReportService.RotationReport> result = reportService.getRotationReport("2026-07-01", "2026-07-31");

        assertThat(result.get(0).getProductName()).isEqualTo("Fernet");
        assertThat(result.get(0).getCategory()).isEqualTo("Birras");
    }

    // ========================================
    // Stock Value Report
    // ========================================

    @Test
    void stockValueReportCalculatesTotalValue() throws SQLException {
        Product a = createProduct(1L, "Coca-Cola", "Gaseosas", 300.0, 600.0);
        Product b = createProduct(2L, "Pepsi", "Gaseosas", 500.0, 650.0);
        when(productRepository.findAllActive()).thenReturn(Arrays.asList(a, b));

        // Product A: 50 units in stock, Product B: 20 units in stock
        when(stockMovementRepository.computeCurrentStocks(Arrays.asList(1L, 2L)))
                .thenReturn(java.util.Map.of(1L, 50, 2L, 20));

        List<ReportService.StockValueReport> result = reportService.getStockValueReport();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductName()).isEqualTo("Coca-Cola");
        assertThat(result.get(0).getCurrentStock()).isEqualTo(50);
        assertThat(result.get(0).getTotalValue()).isCloseTo(15000.0, org.assertj.core.data.Offset.offset(0.01));

        assertThat(result.get(1).getProductName()).isEqualTo("Pepsi");
        assertThat(result.get(1).getCurrentStock()).isEqualTo(20);
        assertThat(result.get(1).getTotalValue()).isCloseTo(10000.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void stockValueReportTotalSumIsCorrect() throws SQLException {
        Product a = createProduct(1L, "Coca-Cola", "Gaseosas", 300.0, 600.0);
        Product b = createProduct(2L, "Pepsi", "Gaseosas", 500.0, 650.0);
        when(productRepository.findAllActive()).thenReturn(Arrays.asList(a, b));

        when(stockMovementRepository.computeCurrentStocks(Arrays.asList(1L, 2L)))
                .thenReturn(java.util.Map.of(1L, 50, 2L, 30));

        double total = reportService.getStockValueReportTotal();

        // (50 * 300) + (30 * 500) = 15000 + 15000 = 30000
        assertThat(total).isCloseTo(30000.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void stockValueReportIncludesZeroStockProducts() throws SQLException {
        Product p = createProduct(1L, "Agua", "Aguas", 200.0, 250.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(p));
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L))).thenReturn(Collections.emptyMap());

        List<ReportService.StockValueReport> result = reportService.getStockValueReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentStock()).isEqualTo(0);
        assertThat(result.get(0).getTotalValue()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
    }

    // ========================================
    // Invalid date edge cases
    // ========================================

    @Test
    @DisplayName("getRotationReport lanza excepción con fecha inválida")
    void getRotationReport_withInvalidDate_throwsException() throws SQLException {
        assertThatThrownBy(() -> reportService.getRotationReport("invalid", "2026-08-01"))
                .isInstanceOf(RuntimeException.class);
    }

    // ========================================
    // Edge cases: zero cost with positive sale price
    // ========================================

    // ========================================
    // Top Sellers Report
    // ========================================

    @Test
    void topSellersReportReturnsLimitedResults() throws SQLException {
        when(saleRepository.findAllActive()).thenThrow(new SQLException("Query failed"));

        assertThatThrownBy(() -> reportService.getTopSellersReport(5))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getMarginReport marca zeroCost cuando costPrice=0 y salePrice>0")
    void marginReport_zeroCostWithPositiveSale() throws SQLException {
        Product product = createProduct(1L, "Promo", "Ofertas", 0.0, 500.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        List<ReportService.MarginReport> result = reportService.getMarginReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isZeroCost()).isTrue();
        assertThat(result.get(0).getMargin()).isCloseTo(500.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.get(0).getMarginPercent()).isEqualTo(0.0);
    }

    // ========================================
    // Hierarchy label in report DTOs
    // ========================================

    @Test
    void marginReportShowsCombinedHierarchyLabel() throws SQLException {
        Product product = createHierarchyProduct(1L, "Quilmes", "Cervezas", "Latas", 300.0, 600.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        List<ReportService.MarginReport> result = reportService.getMarginReport();

        assertThat(result.get(0).getCategory()).isEqualTo("Cervezas — Latas");
    }

    @Test
    void rotationReportShowsCombinedHierarchyLabel() throws SQLException {
        Product product = createHierarchyProduct(1L, "Sprite", "Gaseosas", "Botella", 200.0, 250.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(stockMovementRepository.findByProductIds(Collections.singletonList(1L))).thenReturn(Collections.emptyList());
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L))).thenReturn(Collections.emptyMap());

        List<ReportService.RotationReport> result = reportService.getRotationReport("2026-07-01", "2026-07-31");

        assertThat(result.get(0).getCategory()).isEqualTo("Gaseosas — Botella");
    }

    @Test
    void stockValueReportShowsCombinedHierarchyLabel() throws SQLException {
        Product product = createHierarchyProduct(1L, "Quilmes", "Cervezas", "Latas", 300.0, 600.0);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 10));

        List<ReportService.StockValueReport> result = reportService.getStockValueReport();

        assertThat(result.get(0).getCategory()).isEqualTo("Cervezas — Latas");
    }

    @Test
    void reportShowsBlankLabelForUncategorizedProduct() throws SQLException {
        Product product = createProduct(1L, "Suelto", "Aguas", 200.0, 250.0);
        product.setCategoryName(null);
        product.setSubcategoryName(null);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 0));

        List<ReportService.StockValueReport> result = reportService.getStockValueReport();

        assertThat(result.get(0).getCategory()).isEmpty();
    }

    // ========================================
    // Helpers
    // ========================================

    private Product createProduct(Long id, String name, String category, double cost, double sale) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setCategoryName(category);
        p.setCostPrice(cost);
        p.setSalePrice(sale);
        p.setActive(true);
        return p;
    }

    private Product createHierarchyProduct(Long id, String name, String categoryName,
                                           String subcategoryName, double cost, double sale) {
        Product p = createProduct(id, name, categoryName, cost, sale);
        p.setSubcategoryName(subcategoryName);
        return p;
    }

    private Sale createSale(String date, String channel, double amount) {
        Sale s = new Sale();
        s.setSaleDate(date);
        s.setChannel(channel);
        s.setTotalAmount(amount);
        return s;
    }

    private StockMovement createMovement(Long productId, String type, int quantity) {
        StockMovement m = new StockMovement();
        m.setProductId(productId);
        m.setMovementType(type);
        m.setQuantity(quantity);
        m.setCreatedAt("2026-07-01 10:30:00");
        return m;
    }
}
