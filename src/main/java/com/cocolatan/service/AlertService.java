package com.cocolatan.service;

import com.cocolatan.model.Product;
import com.cocolatan.model.PurchaseItem;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.PurchaseRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.util.StockRisk;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service for alert detection: expiry warnings, low-stock alerts, alert history.
 * Alerts are computed dynamically from purchase lot data and stock levels.
 */
public class AlertService {

    private static final Logger LOGGER = Logger.getLogger(AlertService.class.getName());

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseRepository purchaseRepository;
    private final InventoryService inventoryService;

    private final List<Alert> alertHistory = new ArrayList<>();

    public AlertService(ProductRepository productRepository,
                        StockMovementRepository stockMovementRepository,
                        PurchaseRepository purchaseRepository,
                        InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.purchaseRepository = purchaseRepository;
        this.inventoryService = inventoryService;
    }

    /**
     * Scans all active products and returns expiration alerts.
     * Checks purchase_items for lots expiring within 7 days or already expired.
     */
    public List<Alert> getExpiryAlerts() {
        List<Alert> alerts = new ArrayList<>();
        try {
            List<Product> products = productRepository.findAllActive();
            LocalDate now = LocalDate.now();

            // Single batched query for all purchase items instead of one per product.
            List<Long> productIds = products.stream().map(Product::getId).toList();
            Map<Long, List<PurchaseItem>> itemsByProduct = productIds.isEmpty()
                    ? Map.of()
                    : purchaseRepository.findItemsByProductIds(productIds).stream()
                            .collect(Collectors.groupingBy(PurchaseItem::getProductId));

            for (Product product : products) {
                List<PurchaseItem> purchaseItems = itemsByProduct.getOrDefault(product.getId(), List.of());
                for (PurchaseItem item : purchaseItems) {
                    if (item.getExpiryDate() == null || item.getExpiryDate().isEmpty()) {
                        continue;
                    }
                    try {
                        LocalDate expiryDate = LocalDate.parse(item.getExpiryDate(), DATE_FORMATTER);
                        if (expiryDate.isBefore(now)) {
                            // Expired
                            Alert alert = new Alert("EXPIRED", product, item.getLotNumber(),
                                    item.getExpiryDate(), item.getQuantity());
                            alerts.add(alert);
                        } else if (StockRisk.isExpiringSoon(expiryDate, now)) {
                            // Expiring soon
                            long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(now, expiryDate);
                            Alert alert = new Alert("EXPIRING_SOON", product, item.getLotNumber(),
                                    item.getExpiryDate(), item.getQuantity());
                            alert.setDaysUntilExpiry(daysUntil);
                            alerts.add(alert);
                        }
                    } catch (Exception e) {
                        // Skip a lot with a malformed expiry date — it can't be judged
                        // against the warning window. Logged at FINE to stay observable.
                        LOGGER.log(Level.FINE, "Skipping alert for lot '" + item.getLotNumber()
                                + "' with invalid expiry date '" + item.getExpiryDate() + "'", e);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al detectar alertas de vencimiento", e);
        }

        return alerts;
    }

    /**
     * Scans all active products and returns low-stock alerts.
     * Products where stock < min_stock (stock equal to minimum is NOT low).
     */
    public List<Alert> getLowStockAlerts() {
        List<Alert> alerts = new ArrayList<>();
        try {
            List<Product> products = productRepository.findAllActive();
            List<Long> productIds = products.stream().map(Product::getId).toList();
            Map<Long, Integer> stocks = productIds.isEmpty()
                    ? Map.of()
                    : inventoryService.getStocksForProducts(productIds);
            for (Product product : products) {
                int stock = stocks.getOrDefault(product.getId(), 0);
                if (stock == 0) {
                    Alert alert = new Alert("OUT_OF_STOCK", product, null, null, stock);
                    alerts.add(alert);
                } else if (StockRisk.isBelowMinimum(stock, product.getMinStock())) {
                    Alert alert = new Alert("LOW_STOCK", product, null, null, stock);
                    alerts.add(alert);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al detectar alertas de stock bajo", e);
        }

        return alerts;
    }

    /**
     * Returns all active alerts (expiry + low stock).
     * Alerts are generated fresh from current data every time.
     */
    public List<Alert> getAllAlerts() {
        List<Alert> all = new ArrayList<>();
        all.addAll(getExpiryAlerts());
        all.addAll(getLowStockAlerts());
        return all;
    }

    /**
     * Returns total alert count for badge display.
     * Pure computation — does NOT accumulate into alertHistory.
     */
    public int getAlertCount() {
        int count = 0;
        try {
            List<Product> products = productRepository.findAllActive();
            LocalDate now = LocalDate.now();
            List<Long> productIds = products.stream().map(Product::getId).toList();
            Map<Long, Integer> stocks = productIds.isEmpty()
                    ? Map.of()
                    : inventoryService.getStocksForProducts(productIds);
            Map<Long, List<com.cocolatan.model.PurchaseItem>> itemsByProduct = productIds.isEmpty()
                    ? Map.of()
                    : purchaseRepository.findItemsByProductIds(productIds).stream()
                            .collect(Collectors.groupingBy(com.cocolatan.model.PurchaseItem::getProductId));
            for (Product product : products) {
                // Low-stock / out-of-stock
                int stock = stocks.getOrDefault(product.getId(), 0);
                if (stock == 0 || StockRisk.isBelowMinimum(stock, product.getMinStock())) {
                    count++;
                }
                // Expiry
                List<com.cocolatan.model.PurchaseItem> purchaseItems =
                        itemsByProduct.getOrDefault(product.getId(), List.of());
                for (com.cocolatan.model.PurchaseItem item : purchaseItems) {
                    if (item.getExpiryDate() == null || item.getExpiryDate().isEmpty()) {
                        continue;
                    }
                    try {
                        LocalDate expiry = LocalDate.parse(item.getExpiryDate(), DATE_FORMATTER);
                        if (StockRisk.isExpiryAlert(expiry, now)) {
                            count++;
                        }
                    } catch (Exception e) {
                        // Skip invalid dates — cannot count a lot we can't parse.
                        // Logged at FINE to stay observable.
                        LOGGER.log(Level.FINE, "Skipping count for lot '" + item.getLotNumber()
                                + "' with invalid expiry date '" + item.getExpiryDate() + "'", e);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al contar alertas", e);
            throw new RuntimeException("Error al contar alertas", e);
        }
        return count;
    }

    /**
     * Returns alert history.
     */
    public List<Alert> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }

    /**
     * Clears alert history.
     */
    public void clearHistory() {
        alertHistory.clear();
    }

    /**
     * Alert data class representing a single alert.
     */
    public static class Alert {
        private final String type; // EXPIRED, EXPIRING_SOON, LOW_STOCK, OUT_OF_STOCK
        private final Product product;
        private final String lotNumber;
        private final String expiryDate;
        private final int affectedQuantity;
        private long daysUntilExpiry;
        private boolean dismissed;

        public Alert(String type, Product product, String lotNumber, String expiryDate, int affectedQuantity) {
            this.type = type;
            this.product = product;
            this.lotNumber = lotNumber;
            this.expiryDate = expiryDate;
            this.affectedQuantity = affectedQuantity;
            this.dismissed = false;
        }

        public String getType() { return type; }
        public Product getProduct() { return product; }
        public String getProductName() { return product.getName(); }
        public String getLotNumber() { return lotNumber; }
        public String getExpiryDate() { return expiryDate; }
        public int getAffectedQuantity() { return affectedQuantity; }
        public long getDaysUntilExpiry() { return daysUntilExpiry; }
        public void setDaysUntilExpiry(long days) { this.daysUntilExpiry = days; }
        public boolean isDismissed() { return dismissed; }
        public void dismiss() { this.dismissed = true; }
        public boolean isExpired() { return "EXPIRED".equals(type); }
        public boolean isExpiringSoon() { return "EXPIRING_SOON".equals(type); }
        public boolean isOutOfStock() { return "OUT_OF_STOCK".equals(type); }
        public boolean isLowStock() { return "LOW_STOCK".equals(type); }

        public String getSeverity() {
            return switch (type) {
                case "EXPIRED", "OUT_OF_STOCK" -> "CRITICAL";
                case "EXPIRING_SOON", "LOW_STOCK" -> "WARNING";
                default -> "INFO";
            };
        }
    }
}
