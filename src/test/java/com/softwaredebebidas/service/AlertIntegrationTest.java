package com.softwaredebebidas.service;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.PurchaseItem;
import com.softwaredebebidas.model.StockMovement;
import com.softwaredebebidas.repository.AlertDismissalRepository;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.PurchaseRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.repository.SupplierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for alert flows:
 * expiry detection, low-stock detection, badge counts, dismissal.
 */
class AlertIntegrationTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DatabaseManager dbManager;
    private ProductRepository productRepository;
    private PurchaseRepository purchaseRepository;
    private StockMovementRepository stockMovementRepository;
    private InventoryService inventoryService;
    private AlertDismissalRepository alertDismissalRepository;
    private AlertService alertService;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        productRepository = new ProductRepository(dbManager);
        purchaseRepository = new PurchaseRepository(dbManager);
        stockMovementRepository = new StockMovementRepository(dbManager);
        inventoryService = new InventoryService(stockMovementRepository, productRepository);
        alertDismissalRepository = new AlertDismissalRepository(dbManager);
        alertService = new AlertService(productRepository, stockMovementRepository, purchaseRepository,
                inventoryService, alertDismissalRepository);

        // Seed: supplier
        SupplierRepository supplierRepository = new SupplierRepository(dbManager);
        com.softwaredebebidas.model.Supplier supplier = new com.softwaredebebidas.model.Supplier();
        supplier.setName("Distribuidora Norte");
        supplierRepository.save(supplier);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void expiredLotTriggersExpiryAlert() throws SQLException {
        // Create product with purchase that has expired lot
        Product coke = createProduct("Coca-Cola 500ml", 10);
        productRepository.save(coke);

        // Create purchase with expired lot
        com.softwaredebebidas.model.Purchase purchase = new com.softwaredebebidas.model.Purchase();
        purchase.setSupplierId(1L);
        purchase.setInvoiceRef("INV-001");
        purchase.setPurchaseDate("01/06/2026");
        PurchaseItem item = new PurchaseItem();
        item.setProductId(1L);
        item.setQuantity(24);
        item.setUnitCost(350.0);
        item.setLotNumber("LOT-001");
        item.setExpiryDate("01/07/2026"); // Expired
        purchaseRepository.saveWithItems(purchase, java.util.Collections.singletonList(item));

        List<AlertService.Alert> alerts = alertService.getExpiryAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo("EXPIRED");
        assertThat(alerts.get(0).getProductName()).isEqualTo("Coca-Cola 500ml");
    }

    @Test
    void expiringSoonLotTriggersWarning() throws SQLException {
        Product pepsi = createProduct("Pepsi 500ml", 5);
        productRepository.save(pepsi);

        LocalDate nearExpiry = LocalDate.now().plusDays(3);
        com.softwaredebebidas.model.Purchase purchase = new com.softwaredebebidas.model.Purchase();
        purchase.setSupplierId(1L);
        purchase.setInvoiceRef("INV-002");
        purchase.setPurchaseDate("01/07/2026");
        PurchaseItem item = new PurchaseItem();
        item.setProductId(1L);
        item.setQuantity(12);
        item.setUnitCost(300.0);
        item.setLotNumber("LOT-002");
        item.setExpiryDate(nearExpiry.format(DATE_FORMATTER));
        purchaseRepository.saveWithItems(purchase, java.util.Collections.singletonList(item));

        List<AlertService.Alert> alerts = alertService.getExpiryAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo("EXPIRING_SOON");
        assertThat(alerts.get(0).getDaysUntilExpiry()).isEqualTo(3);
    }

    @Test
    void outOfStockTriggersCriticalAlert() throws SQLException {
        Product sprite = createProduct("Sprite 500ml", 5);
        productRepository.save(sprite);
        // No stock movements = stock is 0

        List<AlertService.Alert> alerts = alertService.getLowStockAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo("OUT_OF_STOCK");
        assertThat(alerts.get(0).getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void lowStockTriggersWarningAlert() throws SQLException {
        Product agua = createProduct("Agua Villavicencio", 10);
        productRepository.save(agua);

        // Add some stock but below min
        StockMovement entry = new StockMovement();
        entry.setProductId(1L);
        entry.setMovementType("ENTRY");
        entry.setQuantity(8);
        entry.setReferenceType("PURCHASE");
        stockMovementRepository.insert(entry);

        List<AlertService.Alert> alerts = alertService.getLowStockAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo("LOW_STOCK");
        assertThat(alerts.get(0).getAffectedQuantity()).isEqualTo(8);
        assertThat(alerts.get(0).getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void healthyProductGeneratesNoAlerts() throws SQLException {
        Product coke = createProduct("Coca-Cola 500ml", 5);
        productRepository.save(coke);

        StockMovement entry = new StockMovement();
        entry.setProductId(1L);
        entry.setMovementType("ENTRY");
        entry.setQuantity(24);
        entry.setReferenceType("PURCHASE");
        stockMovementRepository.insert(entry);

        List<AlertService.Alert> alerts = alertService.getLowStockAlerts();

        assertThat(alerts).isEmpty();
    }

    @Test
    void alertCountReflectsTotalAlerts() throws SQLException {
        Product coke = createProduct("Coca-Cola 500ml", 10);
        productRepository.save(coke);

        // Low stock (stock=0, min=10)
        int count = alertService.getAlertCount();

        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void multipleProductsGenerateMultipleAlerts() throws SQLException {
        Product p1 = createProduct("Coca-Cola 500ml", 10);
        Product p2 = createProduct("Pepsi 500ml", 5);
        productRepository.save(p1);
        productRepository.save(p2);

        // Both have 0 stock
        List<AlertService.Alert> alerts = alertService.getLowStockAlerts();

        assertThat(alerts).hasSize(2);
    }

    @Test
    void alertHistoryIsEmptyWithoutManualAdd() throws SQLException {
        Product coke = createProduct("Coca-Cola 500ml", 10);
        productRepository.save(coke);

        alertService.getLowStockAlerts();
        alertService.getLowStockAlerts();

        // Alerts are generated fresh each time — no auto-accumulation
        assertThat(alertService.getAlertHistory()).isEmpty();
    }

    private Product createProduct(String name, int minStock) {
        Product product = new Product();
        product.setName(name);
        product.setCategory("Gaseosas");
        product.setPresentation("Botella 500ml");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        product.setSupplierId(1L);
        product.setMinStock(minStock);
        product.setActive(true);
        return product;
    }
}
