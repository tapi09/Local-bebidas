package com.cocolatan.service;

import com.cocolatan.model.Product;
import com.cocolatan.model.PurchaseItem;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.PurchaseRepository;
import com.cocolatan.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private InventoryService inventoryService;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(productRepository, stockMovementRepository, purchaseRepository, inventoryService);
    }

    // --- Expiry alert tests ---

    @Test
    void getExpiryAlertsReturnsExpiredProduct() throws SQLException {
        Product product = createProduct(1L, "Coca-Cola 500ml");
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        PurchaseItem item = createPurchaseItem(1L, "LOT-001", "01/01/2025", 10);
        when(purchaseRepository.findItemsByProductIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(item));

        List<AlertService.Alert> alerts = alertService.getExpiryAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo("EXPIRED");
        assertThat(alerts.get(0).getProductName()).isEqualTo("Coca-Cola 500ml");
        assertThat(alerts.get(0).isExpired()).isTrue();
    }

    @Test
    void getExpiryAlertsReturnsExpiringSoonProduct() throws SQLException {
        Product product = createProduct(1L, "Pepsi 500ml");
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        LocalDate nearFuture = LocalDate.now().plusDays(3);
        PurchaseItem item = createPurchaseItem(1L, "LOT-002", nearFuture.format(DATE_FORMATTER), 5);
        when(purchaseRepository.findItemsByProductIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(item));

        List<AlertService.Alert> alerts = alertService.getExpiryAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo("EXPIRING_SOON");
        assertThat(alerts.get(0).getDaysUntilExpiry()).isEqualTo(3);
        assertThat(alerts.get(0).isExpiringSoon()).isTrue();
    }

    @Test
    void getExpiryAlertsReturnsEmptyWhenNoExpiringProducts() throws SQLException {
        Product product = createProduct(1L, "Agua 500ml");
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        LocalDate farFuture = LocalDate.now().plusDays(30);
        PurchaseItem item = createPurchaseItem(1L, "LOT-003", farFuture.format(DATE_FORMATTER), 20);
        when(purchaseRepository.findItemsByProductIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(item));

        List<AlertService.Alert> alerts = alertService.getExpiryAlerts();

        assertThat(alerts).isEmpty();
    }

    @Test
    void getExpiryAlertsSkipsNullExpiryDate() throws SQLException {
        Product product = createProduct(1L, "Test Product");
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        PurchaseItem item = createPurchaseItem(1L, null, null, 10);
        when(purchaseRepository.findItemsByProductIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(item));

        List<AlertService.Alert> alerts = alertService.getExpiryAlerts();

        assertThat(alerts).isEmpty();
    }

    @Test
    void getExpiryAlertsSkipsEmptyExpiryDate() throws SQLException {
        Product product = createProduct(1L, "Test Product");
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        PurchaseItem item = createPurchaseItem(1L, null, "", 10);
        when(purchaseRepository.findItemsByProductIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(item));

        List<AlertService.Alert> alerts = alertService.getExpiryAlerts();

        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("getExpiryAlerts loguea FINE y omite fechas inválidas (no rompe la alerta)")
    void getExpiryAlerts_logsAtFineLevelForInvalidDate() throws SQLException {
        Product product = createProduct(1L, "Test Product");
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));

        PurchaseItem item = createPurchaseItem(1L, "LOT-BAD", "not-a-date", 10);
        when(purchaseRepository.findItemsByProductIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(item));

        Logger logger = Logger.getLogger(AlertService.class.getName());
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) { records.add(record); }
            @Override
            public void flush() { }
            @Override
            public void close() { }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        try {
            List<AlertService.Alert> alerts = alertService.getExpiryAlerts();

            assertThat(alerts).isEmpty();
            assertThat(records)
                    .anyMatch(r -> r.getLevel() == Level.FINE
                            && r.getMessage().contains("not-a-date"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    @DisplayName("getAlertCount loguea FINE y sigue contando ante fecha inválida en un lote")
    void getAlertCount_logsAtFineLevelForInvalidDate() throws SQLException {
        Product product = createProduct(1L, "Test Product");
        product.setMinStock(10);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(inventoryService.getStocksForProducts(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 5));

        PurchaseItem invalid = createPurchaseItem(1L, "LOT-BAD", "not-a-date", 10);
        PurchaseItem valid = createPurchaseItem(1L, "LOT-OK", "01/01/2025", 10);
        when(purchaseRepository.findItemsByProductIds(Collections.singletonList(1L)))
                .thenReturn(Arrays.asList(invalid, valid));

        Logger logger = Logger.getLogger(AlertService.class.getName());
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) { records.add(record); }
            @Override
            public void flush() { }
            @Override
            public void close() { }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        try {
            int count = alertService.getAlertCount();

            assertThat(count).isGreaterThanOrEqualTo(2);
            assertThat(records)
                    .anyMatch(r -> r.getLevel() == Level.FINE
                            && r.getMessage().contains("not-a-date"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }

    // --- Low stock alert tests ---

    @Test
    void getLowStockAlertsReturnsOutOfStockProduct() throws SQLException {
        Product product = createProduct(1L, "Sprite 500ml");
        product.setMinStock(5);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(inventoryService.getStocksForProducts(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 0));

        List<AlertService.Alert> alerts = alertService.getLowStockAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo("OUT_OF_STOCK");
        assertThat(alerts.get(0).isOutOfStock()).isTrue();
    }

    @Test
    void getLowStockAlertsReturnsLowStockProduct() throws SQLException {
        Product product = createProduct(1L, "Agua 500ml");
        product.setMinStock(10);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(inventoryService.getStocksForProducts(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 8));

        List<AlertService.Alert> alerts = alertService.getLowStockAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo("LOW_STOCK");
        assertThat(alerts.get(0).getAffectedQuantity()).isEqualTo(8);
    }

    @Test
    void getLowStockAlertsReturnsEmptyWhenAllProductsOk() throws SQLException {
        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setMinStock(5);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(inventoryService.getStocksForProducts(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 24));

        List<AlertService.Alert> alerts = alertService.getLowStockAlerts();

        assertThat(alerts).isEmpty();
    }

    @Test
    void getLowStockAlertsHandlesMultipleProducts() throws SQLException {
        Product p1 = createProduct(1L, "Coca-Cola", 10);
        Product p2 = createProduct(2L, "Pepsi", 5);
        Product p3 = createProduct(3L, "Sprite", 5);
        when(productRepository.findAllActive()).thenReturn(Arrays.asList(p1, p2, p3));
        when(inventoryService.getStocksForProducts(Arrays.asList(1L, 2L, 3L)))
                .thenReturn(java.util.Map.of(1L, 24, 2L, 3, 3L, 0)); // OK, LOW, OUT

        List<AlertService.Alert> alerts = alertService.getLowStockAlerts();

        assertThat(alerts).hasSize(2);
        assertThat(alerts).extracting(AlertService.Alert::getType)
                .containsExactlyInAnyOrder("LOW_STOCK", "OUT_OF_STOCK");
    }

    // --- Combined alerts ---

    @Test
    void getAllAlertsReturnsBothExpiryAndLowStock() throws SQLException {
        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setMinStock(10);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(inventoryService.getStocksForProducts(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 5));

        LocalDate pastDate = LocalDate.now().minusDays(5);
        PurchaseItem item = createPurchaseItem(1L, "LOT-001", pastDate.format(DATE_FORMATTER), 10);
        when(purchaseRepository.findItemsByProductIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(item));

        List<AlertService.Alert> allAlerts = alertService.getAllAlerts();

        assertThat(allAlerts.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getAlertCountReturnsTotalAlerts() throws SQLException {
        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setMinStock(10);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(inventoryService.getStocksForProducts(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 5));

        LocalDate pastDate = LocalDate.now().minusDays(5);
        PurchaseItem item = createPurchaseItem(1L, "LOT-001", pastDate.format(DATE_FORMATTER), 10);
        when(purchaseRepository.findItemsByProductIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(item));

        int count = alertService.getAlertCount();

        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getAlertCountThrowsOnSqlException() throws SQLException {
        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setMinStock(10);
        when(productRepository.findAllActive()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> alertService.getAlertCount())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al contar alertas");
    }

    // --- Alert properties tests ---

    @Test
    void alertSeverityIsCriticalForExpired() {
        AlertService.Alert alert = new AlertService.Alert("EXPIRED", createProduct(1L, "Test"), "LOT-001", "01/01/2025", 5);
        assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void alertSeverityIsCriticalForOutOfStock() {
        AlertService.Alert alert = new AlertService.Alert("OUT_OF_STOCK", createProduct(1L, "Test"), null, null, 0);
        assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void alertSeverityIsWarningForExpiringSoon() {
        AlertService.Alert alert = new AlertService.Alert("EXPIRING_SOON", createProduct(1L, "Test"), "LOT-001", "25/07/2026", 5);
        assertThat(alert.getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void alertSeverityIsWarningForLowStock() {
        AlertService.Alert alert = new AlertService.Alert("LOW_STOCK", createProduct(1L, "Test"), null, null, 3);
        assertThat(alert.getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void alertDismissSetsDismissedFlag() {
        AlertService.Alert alert = new AlertService.Alert("LOW_STOCK", createProduct(1L, "Test"), null, null, 3);
        assertThat(alert.isDismissed()).isFalse();
        alert.dismiss();
        assertThat(alert.isDismissed()).isTrue();
    }

    // --- History tests ---

    @Test
    void alertHistoryIsEmptyWhenNoManualAdd() throws SQLException {
        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setMinStock(10);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(inventoryService.getStocksForProducts(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 5));

        alertService.getLowStockAlerts();
        alertService.getLowStockAlerts();

        // Alerts are generated fresh each time — no auto-accumulation
        assertThat(alertService.getAlertHistory()).isEmpty();
    }

    @Test
    void clearHistoryRemovesAllAlerts() throws SQLException {
        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setMinStock(10);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(inventoryService.getStocksForProducts(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 5));

        alertService.getLowStockAlerts();
        alertService.clearHistory();

        assertThat(alertService.getAlertHistory()).isEmpty();
    }

    private Product createProduct(Long id, String name) {
        return createProduct(id, name, 0);
    }

    private Product createProduct(Long id, String name, int minStock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setCategory("Gaseosas");
        product.setMinStock(minStock);
        return product;
    }

    private PurchaseItem createPurchaseItem(Long productId, String lotNumber, String expiryDate, int quantity) {
        PurchaseItem item = new PurchaseItem();
        item.setProductId(productId);
        item.setLotNumber(lotNumber);
        item.setExpiryDate(expiryDate);
        item.setQuantity(quantity);
        return item;
    }
}
