package com.cocolatan.presenter;

import com.cocolatan.model.Product;
import com.cocolatan.model.StockMovement;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockPresenterTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private StockPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new StockPresenter(inventoryService, productRepository, stockMovementRepository);
    }

    // --- Dashboard tests ---

    @Test
    void getDashboardDataReturnsProductsWithStockInfo() throws SQLException {
        Product p1 = createProduct(1L, "Coca-Cola 500ml", 10);
        Product p2 = createProduct(2L, "Pepsi 500ml", 5);
        when(productRepository.findAllActive()).thenReturn(Arrays.asList(p1, p2));
        java.util.Map<Long, Integer> stockMap = new java.util.HashMap<>();
        stockMap.put(1L, 24);
        stockMap.put(2L, 3);
        when(stockMovementRepository.computeCurrentStocks(Arrays.asList(1L, 2L))).thenReturn(stockMap);

        List<StockPresenter.ProductStockInfo> data = presenter.getDashboardData();

        assertThat(data).hasSize(2);
        assertThat(data.get(0).getProductName()).isEqualTo("Coca-Cola 500ml");
        assertThat(data.get(0).getCurrentStock()).isEqualTo(24);
        assertThat(data.get(0).getStatus()).isEqualTo("OK");
        assertThat(data.get(1).getStatus()).isEqualTo("LOW");
    }

    @Test
    void getDashboardByStatusFiltersCorrectly() throws SQLException {
        Product p1 = createProduct(1L, "Coca-Cola", 10);
        Product p2 = createProduct(2L, "Pepsi", 5);
        Product p3 = createProduct(3L, "Sprite", 5);
        when(productRepository.findAllActive()).thenReturn(Arrays.asList(p1, p2, p3));
        java.util.Map<Long, Integer> stockMap = new java.util.HashMap<>();
        stockMap.put(1L, 24);
        stockMap.put(2L, 3);
        stockMap.put(3L, 0);
        when(stockMovementRepository.computeCurrentStocks(Arrays.asList(1L, 2L, 3L))).thenReturn(stockMap);

        List<StockPresenter.ProductStockInfo> lowProducts = presenter.getDashboardByStatus("LOW");

        assertThat(lowProducts).hasSize(1);
        assertThat(lowProducts.get(0).getProductName()).isEqualTo("Pepsi");
    }

    @Test
    void getDashboardByStatusReturnsEmptyForNoMatch() throws SQLException {
        Product p1 = createProduct(1L, "Coca-Cola", 10);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(p1));
        java.util.Map<Long, Integer> stockMap = new java.util.HashMap<>();
        stockMap.put(1L, 24);
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L))).thenReturn(stockMap);

        List<StockPresenter.ProductStockInfo> outProducts = presenter.getDashboardByStatus("OUT");

        assertThat(outProducts).isEmpty();
    }

    // --- Movement history tests ---

    @Test
    void getMovementHistoryDelegatesToRepository() throws SQLException {
        List<StockMovement> movements = Arrays.asList(new StockMovement(), new StockMovement());
        when(stockMovementRepository.findByProductId(1L)).thenReturn(movements);

        List<StockMovement> result = presenter.getMovementHistory(1L);

        assertThat(result).hasSize(2);
        verify(stockMovementRepository).findByProductId(1L);
    }

    @Test
    void getMovementHistoryByTypeFiltersCorrectly() throws SQLException {
        List<StockMovement> entries = Collections.singletonList(new StockMovement());
        when(stockMovementRepository.findByProductIdAndType(1L, "ENTRY")).thenReturn(entries);

        List<StockMovement> result = presenter.getMovementHistoryByType(1L, "ENTRY");

        assertThat(result).hasSize(1);
    }

    // --- Adjustment tests ---

    @Test
    void createAdjustmentCreatesMovement() throws SQLException {
        when(stockMovementRepository.insert(any(StockMovement.class))).thenReturn(1L);

        boolean result = presenter.createAdjustment(1L, 5, "IN", "Corrección de entrega dañada");

        assertThat(result).isTrue();
        verify(stockMovementRepository).insert(argThat(movement ->
                "ADJUSTMENT".equals(movement.getMovementType()) &&
                "ADJUSTMENT".equals(movement.getReferenceType()) &&
                movement.getQuantity() == 5 &&
                "Corrección de entrega dañada".equals(movement.getNotes())
        ));
    }

    @Test
    void createAdjustmentRejectsZeroQuantity() throws SQLException {
        boolean result = presenter.createAdjustment(1L, 0, "IN", "Reason");

        assertThat(result).isFalse();
        verify(stockMovementRepository, never()).insert(any());
    }

    @Test
    void createAdjustmentRejectsNegativeQuantity() throws SQLException {
        boolean result = presenter.createAdjustment(1L, -3, "IN", "Reason");

        assertThat(result).isFalse();
        verify(stockMovementRepository, never()).insert(any());
    }

    @Test
    void createAdjustmentRejectsEmptyReason() throws SQLException {
        boolean result = presenter.createAdjustment(1L, 5, "IN", "");

        assertThat(result).isFalse();
        verify(stockMovementRepository, never()).insert(any());
    }

    @Test
    void createAdjustmentRejectsNullReason() throws SQLException {
        boolean result = presenter.createAdjustment(1L, 5, "IN", null);

        assertThat(result).isFalse();
        verify(stockMovementRepository, never()).insert(any());
    }

    @Test
    void createAdjustmentRejectsInvalidDirection() throws SQLException {
        boolean result = presenter.createAdjustment(1L, 5, "LEFT", "Reason");

        assertThat(result).isFalse();
        verify(stockMovementRepository, never()).insert(any());
    }

    @Test
    void createAdjustmentAcceptsINDirection() throws SQLException {
        when(stockMovementRepository.insert(any(StockMovement.class))).thenReturn(1L);

        boolean result = presenter.createAdjustment(1L, 5, "IN", "Reposición");

        assertThat(result).isTrue();
    }

    @Test
    void createAdjustmentAcceptsOUTDirection() throws SQLException {
        when(stockMovementRepository.insert(any(StockMovement.class))).thenReturn(1L);

        boolean result = presenter.createAdjustment(1L, 3, "OUT", "Producto dañado");

        assertThat(result).isTrue();
    }

    // --- Status counts tests ---

    @Test
    void getStatusCountsReturnsCorrectCounts() throws SQLException {
        Product p1 = createProduct(1L, "A", 10);
        Product p2 = createProduct(2L, "B", 5);
        Product p3 = createProduct(3L, "C", 4);
        Product p4 = createProduct(4L, "D", 0);
        when(productRepository.findAllActive()).thenReturn(Arrays.asList(p1, p2, p3, p4));
        java.util.Map<Long, Integer> stockMap = new java.util.HashMap<>();
        stockMap.put(1L, 24);
        stockMap.put(2L, 3);
        stockMap.put(3L, 5);
        stockMap.put(4L, 0);
        when(stockMovementRepository.computeCurrentStocks(Arrays.asList(1L, 2L, 3L, 4L))).thenReturn(stockMap);

        StockPresenter.StockStatusCounts counts = presenter.getStatusCounts();

        assertThat(counts.getOk()).isEqualTo(2);
        assertThat(counts.getLow()).isEqualTo(1);
        assertThat(counts.getOut()).isEqualTo(1);
        assertThat(counts.getTotal()).isEqualTo(4);
    }

    // --- Low stock products ---

    @Test
    void getLowStockProductsDelegatesToInventoryService() {
        List<Product> lowProducts = Arrays.asList(createProduct(1L, "Low Product", 10));
        when(inventoryService.getLowStockProducts()).thenReturn(lowProducts);

        List<Product> result = presenter.getLowStockProducts();

        assertThat(result).hasSize(1);
        verify(inventoryService).getLowStockProducts();
    }

    // --- adjustStock tests ---

    @Test
    void adjustStockDelegatesToInventoryService() {
        presenter.adjustStock(1L, 5, "Reposición");

        verify(inventoryService).adjustStock(1L, 5, "Reposición");
    }

    @Test
    void adjustStockNegativeDelegatesToInventoryService() {
        presenter.adjustStock(1L, -3, "Producto dañado");

        verify(inventoryService).adjustStock(1L, -3, "Producto dañado");
    }

    // --- getMovementHistory (with filters) tests ---

    @Test
    void getMovementHistoryWithFiltersDelegatesToRepository() throws SQLException {
        List<StockMovement> movements = Arrays.asList(new StockMovement(), new StockMovement());
        when(stockMovementRepository.findByFilters(1L, "ENTRY", null, null)).thenReturn(movements);

        List<StockMovement> result = presenter.getMovementHistory(1L, "ENTRY", null, null);

        assertThat(result).hasSize(2);
        verify(stockMovementRepository).findByFilters(1L, "ENTRY", null, null);
    }

    @Test
    void getMovementHistoryWithDateFilters() throws SQLException {
        when(stockMovementRepository.findByFilters(null, null, "01/01/2025", "31/12/2025")).thenReturn(Collections.emptyList());

        List<StockMovement> result = presenter.getMovementHistory(null, null, "01/01/2025", "31/12/2025");

        assertThat(result).isEmpty();
        verify(stockMovementRepository).findByFilters(null, null, "01/01/2025", "31/12/2025");
    }

    // --- getAllProducts tests ---

    @Test
    void getAllProductsDelegatesToProductRepository() throws SQLException {
        Product p1 = createProduct(1L, "Coca-Cola", 10);
        Product p2 = createProduct(2L, "Pepsi", 5);
        when(productRepository.findAllActive()).thenReturn(Arrays.asList(p1, p2));

        List<Product> result = presenter.getAllProducts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Coca-Cola");
        verify(productRepository).findAllActive();
    }

    // --- Hierarchy label on dashboard ---

    @Test
    void dashboardShowsHierarchyLabel() throws SQLException {
        Product p = createHierarchyProduct(1L, "Quilmes", "Cervezas", "Latas", 10);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(p));
        java.util.Map<Long, Integer> stockMap = new java.util.HashMap<>();
        stockMap.put(1L, 24);
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L))).thenReturn(stockMap);

        List<StockPresenter.ProductStockInfo> data = presenter.getDashboardData();

        assertThat(data.get(0).getProductCategory()).isEqualTo("Cervezas — Latas");
    }

    @Test
    void dashboardShowsBlankLabelForUncategorizedProduct() throws SQLException {
        Product p = createProduct(1L, "Suelto", 0);
        p.setCategoryName(null);
        p.setSubcategoryName(null);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(p));
        java.util.Map<Long, Integer> stockMap = new java.util.HashMap<>();
        stockMap.put(1L, 5);
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L))).thenReturn(stockMap);

        List<StockPresenter.ProductStockInfo> data = presenter.getDashboardData();

        assertThat(data.get(0).getProductCategory()).isEmpty();
    }

    private Product createProduct(Long id, String name, int minStock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setCategoryName("Gaseosas");
        product.setMinStock(minStock);
        product.setActive(true);
        return product;
    }

    private Product createHierarchyProduct(Long id, String name, String categoryName,
                                           String subcategoryName, int minStock) {
        Product p = createProduct(id, name, minStock);
        p.setCategoryName(categoryName);
        p.setSubcategoryName(subcategoryName);
        return p;
    }
}
