package com.cocolatan.service;

import com.cocolatan.model.Product;
import com.cocolatan.model.PurchaseItem;
import com.cocolatan.repository.AlertDismissalRepository;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.PurchaseRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.util.StockRisk;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final AlertDismissalRepository alertDismissalRepository;

    private final List<Alert> alertHistory = new ArrayList<>();

    public AlertService(ProductRepository productRepository,
                        StockMovementRepository stockMovementRepository,
                        PurchaseRepository purchaseRepository,
                        InventoryService inventoryService,
                        AlertDismissalRepository alertDismissalRepository) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.purchaseRepository = purchaseRepository;
        this.inventoryService = inventoryService;
        this.alertDismissalRepository = alertDismissalRepository;
    }

    /**
     * Scans all active products and returns expiration alerts.
     * Checks purchase_items for lots expiring within 7 days or already expired.
     */
    public List<Alert> getExpiryAlerts() {
        List<Alert> alerts = new ArrayList<>();
        try {
            List<AlertDismissalRepository.Dismissal> dismissals = alertDismissalRepository.findAll();
            Set<String> dismissedKeys = dismissals.stream()
                    .map(AlertDismissalRepository.Dismissal::key)
                    .collect(Collectors.toSet());
            List<Long> activeDismissalIds = new ArrayList<>();

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
                            if (dismissedKeys.contains(alertKey(alert))) {
                                // Persisted dismissal: keep it (still applicable) but hide it.
                                activeDismissalIds.addAll(dismissalIdsFor(alert, dismissals));
                                continue;
                            }
                            alerts.add(alert);
                        } else if (StockRisk.isExpiringSoon(expiryDate, now)) {
                            // Expiring soon
                            long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(now, expiryDate);
                            Alert alert = new Alert("EXPIRING_SOON", product, item.getLotNumber(),
                                    item.getExpiryDate(), item.getQuantity());
                            alert.setDaysUntilExpiry(daysUntil);
                            if (dismissedKeys.contains(alertKey(alert))) {
                                activeDismissalIds.addAll(dismissalIdsFor(alert, dismissals));
                                continue;
                            }
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

            // Opportunistic cleanup: drop dismissals of THIS alert family that no
            // longer apply (so the alert can fire again if the condition returns).
            // Dismissals of other families are preserved — otherwise the expiry
            // cleanup would delete active low-stock dismissals and vice versa.
            alertDismissalRepository.deleteAllExcept(
                    keepDismissalIds(activeDismissalIds, dismissals, Set.of("EXPIRED", "EXPIRING_SOON")));
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
            List<AlertDismissalRepository.Dismissal> dismissals = alertDismissalRepository.findAll();
            Set<String> dismissedKeys = dismissals.stream()
                    .map(AlertDismissalRepository.Dismissal::key)
                    .collect(Collectors.toSet());
            List<Long> activeDismissalIds = new ArrayList<>();

            List<Product> products = productRepository.findAllActive();
            List<Long> productIds = products.stream().map(Product::getId).toList();
            Map<Long, Integer> stocks = productIds.isEmpty()
                    ? Map.of()
                    : inventoryService.getStocksForProducts(productIds);
            for (Product product : products) {
                int stock = stocks.getOrDefault(product.getId(), 0);
                if (stock == 0) {
                    Alert alert = new Alert("OUT_OF_STOCK", product, null, null, stock);
                    if (dismissedKeys.contains(alertKey(alert))) {
                        activeDismissalIds.addAll(dismissalIdsFor(alert, dismissals));
                        continue;
                    }
                    alerts.add(alert);
                } else if (StockRisk.isBelowMinimum(stock, product.getMinStock())) {
                    Alert alert = new Alert("LOW_STOCK", product, null, null, stock);
                    if (dismissedKeys.contains(alertKey(alert))) {
                        activeDismissalIds.addAll(dismissalIdsFor(alert, dismissals));
                        continue;
                    }
                    alerts.add(alert);
                }
            }

            // Opportunistic cleanup: drop dismissals of THIS alert family that no
            // longer apply; preserve dismissals of other families (see getExpiryAlerts).
            alertDismissalRepository.deleteAllExcept(
                    keepDismissalIds(activeDismissalIds, dismissals, Set.of("OUT_OF_STOCK", "LOW_STOCK")));
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
     * Dismisses an alert. Persists the dismissal so it does not reappear after
     * a restart, and marks the in-memory instance so the current view hides it.
     */
    public void dismiss(Alert alert) {
        if (alert == null || alert.getProduct() == null) {
            return;
        }
        alert.dismiss();
        try {
            alertDismissalRepository.dismiss(alert.getType(), alert.getProduct().getId(),
                    alert.getLotNumber() == null || alert.getLotNumber().isEmpty() ? null : alert.getLotNumber());
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "No se pudo persistir el descarte de la alerta", e);
        }
    }

    /**
     * Composite dismissal key for an alert instance. Mirrors
     * {@link AlertDismissalRepository.Dismissal#key()}: type|productId|lot
     * with the lot normalized to an empty string when absent.
     */
    private static String alertKey(Alert alert) {
        return alertKey(alert.getType(), alert.getProduct().getId(), alert.getLotNumber());
    }

    private static String alertKey(String type, Long productId, String lotNumber) {
        return type + "|" + productId + "|" + (lotNumber == null ? "" : lotNumber);
    }

    /** Ids of persisted dismissals whose composite key matches the given alert. */
    private static List<Long> dismissalIdsFor(Alert alert, List<AlertDismissalRepository.Dismissal> dismissals) {
        String key = alertKey(alert);
        return dismissals.stream()
                .filter(d -> d.key().equals(key))
                .map(AlertDismissalRepository.Dismissal::getId)
                .toList();
    }

    /**
     * Builds the keep-set for the opportunistic cleanup: the ids of still-active
     * dismissals of {@code ownTypes} plus every dismissal of a different family.
     * Preserving foreign-family ids is what keeps e.g. the expiry cleanup from
     * deleting active low-stock dismissals (the alerts view calls both getters
     * back to back on every refresh).
     */
    private static List<Long> keepDismissalIds(List<Long> activeIds,
                                               List<AlertDismissalRepository.Dismissal> dismissals,
                                               Set<String> ownTypes) {
        Set<Long> keep = new HashSet<>(activeIds);
        for (AlertDismissalRepository.Dismissal d : dismissals) {
            if (!ownTypes.contains(d.getAlertType())) {
                keep.add(d.getId());
            }
        }
        return new ArrayList<>(keep);
    }

    /**
     * Returns total alert count for badge display.
     * Pure computation — does NOT accumulate into alertHistory.
     */
    public int getAlertCount() {
        int count = 0;
        try {
            List<AlertDismissalRepository.Dismissal> dismissals = alertDismissalRepository.findAll();
            Set<String> dismissedKeys = dismissals.stream()
                    .map(AlertDismissalRepository.Dismissal::key)
                    .collect(Collectors.toSet());

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
                if (stock == 0) {
                    if (!dismissedKeys.contains(alertKey("OUT_OF_STOCK", product.getId(), null))) {
                        count++;
                    }
                } else if (StockRisk.isBelowMinimum(stock, product.getMinStock())) {
                    if (!dismissedKeys.contains(alertKey("LOW_STOCK", product.getId(), null))) {
                        count++;
                    }
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
                            String alertType = expiry.isBefore(now) ? "EXPIRED" : "EXPIRING_SOON";
                            if (!dismissedKeys.contains(alertKey(alertType, product.getId(), item.getLotNumber()))) {
                                count++;
                            }
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
