package com.cocolatan.repository;

import com.cocolatan.model.Product;
import com.cocolatan.model.StockMovement;
import com.cocolatan.model.Supplier;
import com.cocolatan.presenter.StockPresenter;
import com.cocolatan.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for stock management flows:
 * dashboard, status indicators, movement history, manual adjustments.
 */
class StockIntegrationTest {

    private DatabaseManager dbManager;
    private StockMovementRepository stockMovementRepository;
    private ProductRepository productRepository;
    private InventoryService inventoryService;
    private StockPresenter presenter;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        stockMovementRepository = new StockMovementRepository(dbManager);
        productRepository = new ProductRepository(dbManager);
        inventoryService = new InventoryService(stockMovementRepository, productRepository);
        presenter = new StockPresenter(inventoryService, productRepository, stockMovementRepository);

        // Seed: supplier + products
        SupplierRepository supplierRepository = new SupplierRepository(dbManager);
        Supplier supplier = new Supplier();
        supplier.setName("Distribuidora Norte");
        supplierRepository.save(supplier);

        Product coke = createProduct("Coca-Cola 500ml", "Gaseosas", 10);
        productRepository.save(coke);

        Product pepsi = createProduct("Pepsi 500ml", "Gaseosas", 5);
        productRepository.save(pepsi);

        Product sprite = createProduct("Sprite 500ml", "Gaseosas", 5);
        productRepository.save(sprite);

        // Stock: coke=24, pepsi=3 (LOW), sprite=0 (OUT)
        addStockMovement(1L, "ENTRY", 24);
        addStockMovement(2L, "ENTRY", 3);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void dashboardShowsCorrectStockLevels() {
        List<StockPresenter.ProductStockInfo> data = presenter.getDashboardData();

        assertThat(data).hasSize(3);

        StockPresenter.ProductStockInfo coke = data.stream()
                .filter(d -> d.getProductName().equals("Coca-Cola 500ml"))
                .findFirst().orElse(null);
        assertThat(coke).isNotNull();
        assertThat(coke.getCurrentStock()).isEqualTo(24);
        assertThat(coke.getStatus()).isEqualTo("OK");

        StockPresenter.ProductStockInfo pepsi = data.stream()
                .filter(d -> d.getProductName().equals("Pepsi 500ml"))
                .findFirst().orElse(null);
        assertThat(pepsi).isNotNull();
        assertThat(pepsi.getCurrentStock()).isEqualTo(3);
        assertThat(pepsi.getStatus()).isEqualTo("LOW");

        StockPresenter.ProductStockInfo sprite = data.stream()
                .filter(d -> d.getProductName().equals("Sprite 500ml"))
                .findFirst().orElse(null);
        assertThat(sprite).isNotNull();
        assertThat(sprite.getCurrentStock()).isEqualTo(0);
        assertThat(sprite.getStatus()).isEqualTo("OUT");
    }

    @Test
    void dashboardFilterByLOWStatus() {
        List<StockPresenter.ProductStockInfo> lowProducts = presenter.getDashboardByStatus("LOW");

        assertThat(lowProducts).hasSize(1);
        assertThat(lowProducts.get(0).getProductName()).isEqualTo("Pepsi 500ml");
    }

    @Test
    void dashboardFilterByOUTStatus() {
        List<StockPresenter.ProductStockInfo> outProducts = presenter.getDashboardByStatus("OUT");

        assertThat(outProducts).hasSize(1);
        assertThat(outProducts.get(0).getProductName()).isEqualTo("Sprite 500ml");
    }

    @Test
    void movementHistoryShowsAllMovementsForProduct() throws SQLException {
        addStockMovement(1L, "EXIT", 2);
        addStockMovement(1L, "ENTRY", 6);

        List<StockMovement> movements = presenter.getMovementHistory(1L);

        assertThat(movements).hasSize(3); // ENTRY 24, EXIT 2, ENTRY 6
    }

    @Test
    void movementHistoryFilterByType() throws SQLException {
        addStockMovement(1L, "EXIT", 2);
        addStockMovement(1L, "ENTRY", 6);

        List<StockMovement> entries = presenter.getMovementHistoryByType(1L, "ENTRY");

        assertThat(entries).hasSize(2); // ENTRY 24, ENTRY 6
    }

    @Test
    void manualAdjustmentINIncreasesStock() {
        boolean result = presenter.createAdjustment(1L, 3, "IN", "Corrección de entrega dañada");

        assertThat(result).isTrue();
        assertThat(inventoryService.getCurrentStock(1L)).isEqualTo(27); // 24 + 3
    }

    @Test
    void manualAdjustmentOUTDecreasesStock() {
        boolean result = presenter.createAdjustment(1L, 4, "OUT", "Producto expirado removido");

        assertThat(result).isTrue();
        assertThat(inventoryService.getCurrentStock(1L)).isEqualTo(20); // 24 - 4
    }

    @Test
    void adjustmentCreatesCorrectMovementRecord() throws SQLException {
        presenter.createAdjustment(1L, 5, "IN", "Reposición de inventario");

        List<StockMovement> movements = stockMovementRepository.findByProductIdAndType(1L, "ADJUSTMENT");

        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getQuantity()).isEqualTo(5);
        assertThat(movements.get(0).getReferenceType()).isEqualTo("ADJUSTMENT");
        assertThat(movements.get(0).getNotes()).isEqualTo("Reposición de inventario");
    }

    @Test
    void adjustmentRejectsEmptyReason() {
        boolean result = presenter.createAdjustment(1L, 5, "IN", "");

        assertThat(result).isFalse();
    }

    @Test
    void adjustmentRejectsZeroQuantity() {
        boolean result = presenter.createAdjustment(1L, 0, "IN", "Reason");

        assertThat(result).isFalse();
    }

    @Test
    void statusCountsReflectCurrentState() {
        StockPresenter.StockStatusCounts counts = presenter.getStatusCounts();

        assertThat(counts.getOk()).isEqualTo(1);     // coke
        assertThat(counts.getLow()).isEqualTo(1);    // pepsi
        assertThat(counts.getOut()).isEqualTo(1);    // sprite
        assertThat(counts.getTotal()).isEqualTo(3);
    }

    @Test
    void stockFormulaEntryMinusExit() throws SQLException {
        // coke: 24 ENTRY, then EXIT 6
        addStockMovement(1L, "EXIT", 6);

        int stock = inventoryService.getCurrentStock(1L);
        assertThat(stock).isEqualTo(18); // 24 - 6
    }

    @Test
    void stockFormulaWithAdjustment() throws SQLException {
        // coke: 24 ENTRY, EXIT 6, ADJUSTMENT +2
        addStockMovement(1L, "EXIT", 6);
        addStockMovement(1L, "ADJUSTMENT", 2);

        int stock = inventoryService.getCurrentStock(1L);
        assertThat(stock).isEqualTo(20); // 24 - 6 + 2
    }

    // --- New feature tests: adjustStock, getMovementHistory with filters, getAllProducts ---

    @Test
    void adjustStockPositiveAddsStock() {
        presenter.adjustStock(1L, 5, "Reposición de inventario");

        assertThat(inventoryService.getCurrentStock(1L)).isEqualTo(29); // 24 + 5
    }

    @Test
    void adjustStockNegativeRemovesStock() {
        presenter.adjustStock(1L, -4, "Producto dañado removido");

        assertThat(inventoryService.getCurrentStock(1L)).isEqualTo(20); // 24 - 4
    }

    @Test
    void adjustStockCreatesMovementRecord() throws SQLException {
        presenter.adjustStock(1L, 3, "Corrección de inventario");

        List<StockMovement> adjustments = stockMovementRepository.findByProductIdAndType(1L, "ADJUSTMENT");
        assertThat(adjustments).hasSize(1);
        assertThat(adjustments.get(0).getQuantity()).isEqualTo(3);
        assertThat(adjustments.get(0).getNotes()).isEqualTo("Corrección de inventario");
    }

    @Test
    void getMovementHistoryWithFiltersByProductAndType() throws SQLException {
        addStockMovement(1L, "EXIT", 2);
        addStockMovement(1L, "ENTRY", 6);
        addStockMovement(1L, "ADJUSTMENT", 1);

        List<StockMovement> entries = presenter.getMovementHistory(1L, "ENTRY", null, null);

        assertThat(entries).hasSize(2); // ENTRY 24, ENTRY 6
        assertThat(entries).allMatch(m -> "ENTRY".equals(m.getMovementType()));
    }

    @Test
    void getMovementHistoryWithFiltersReturnsAllWhenNoFilters() throws SQLException {
        addStockMovement(1L, "EXIT", 2);

        List<StockMovement> all = presenter.getMovementHistory(null, null, null, null);

        assertThat(all).hasSize(3); // ENTRY 24, EXIT 2, plus the addStockMovement
    }

    @Test
    void getMovementHistoryWithFiltersByProductOnly() throws SQLException {
        addStockMovement(1L, "EXIT", 2);

        List<StockMovement> pepsiMovements = presenter.getMovementHistory(2L, null, null, null);

        assertThat(pepsiMovements).hasSize(1); // only the initial ENTRY 3
    }

    @Test
    void getAllProductsReturnsActiveProducts() {
        List<Product> products = presenter.getAllProducts();

        assertThat(products).hasSize(3);
        assertThat(products).extracting(Product::getName)
                .containsExactlyInAnyOrder("Coca-Cola 500ml", "Pepsi 500ml", "Sprite 500ml");
    }

    private void addStockMovement(Long productId, String type, int quantity) throws SQLException {
        StockMovement movement = new StockMovement();
        movement.setProductId(productId);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setReferenceType("PURCHASE");
        movement.setReferenceId(1L);
        stockMovementRepository.insert(movement);
    }

    private Product createProduct(String name, String category, int minStock) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setPresentation("Botella 500ml");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        product.setSupplierId(1L);
        product.setMinStock(minStock);
        product.setActive(true);
        return product;
    }
}
