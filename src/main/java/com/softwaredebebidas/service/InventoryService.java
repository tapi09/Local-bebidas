package com.softwaredebebidas.service;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.StockMovement;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.util.StockRisk;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for inventory operations: stock calculation, status indicators, alerts.
 * Includes expiry validation for sales and alert detection.
 */
public class InventoryService {

    private static final Logger LOGGER = Logger.getLogger(InventoryService.class.getName());

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;

    public InventoryService(StockMovementRepository stockMovementRepository, ProductRepository productRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
    }

    /**
     * Returns current stock for a product (ENTRY - EXIT).
     */
    public int getCurrentStock(Long productId) {
        try {
            return stockMovementRepository.computeCurrentStock(productId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al calcular stock del producto " + productId, e);
        }
    }

    /**
     * Returns stock status: OK, LOW, or OUT.
     */
    public String getStockStatus(Long productId) {
        try {
            int stock = stockMovementRepository.computeCurrentStock(productId);
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                return "OUT";
            }
            if (stock <= 0) {
                return "OUT";
            }
            if (StockRisk.isAtOrBelowMinimum(stock, product.getMinStock())) {
                return "LOW";
            }
            return "OK";
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener estado de stock", e);
        }
    }

    /**
     * Returns all active products with stock at or below their min_stock threshold.
     */
    public List<Product> getLowStockProducts() {
        try {
            List<Product> allProducts = productRepository.findAllActive();
            List<Long> productIds = allProducts.stream().map(Product::getId).toList();
            Map<Long, Integer> stocks = productIds.isEmpty()
                    ? Map.of()
                    : stockMovementRepository.computeCurrentStocks(productIds);
            List<Product> lowStock = new ArrayList<>();
            for (Product product : allProducts) {
                int stock = stocks.getOrDefault(product.getId(), 0);
                if (StockRisk.isAtOrBelowMinimum(stock, product.getMinStock())) {
                    lowStock.add(product);
                }
            }
            return lowStock;
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener productos con stock bajo", e);
        }
    }

    /**
     * Validates whether a product has sufficient stock for a given quantity.
     *
     * @return true if stock is sufficient, false otherwise
     */
    public boolean validateStock(Long productId, int requestedQuantity) {
        int currentStock = getCurrentStock(productId);
        return currentStock >= requestedQuantity;
    }

    /**
     * Returns available stock for a product.
     */
    public int getAvailableStock(Long productId) {
        return getCurrentStock(productId);
    }

    /**
     * Computes current stock for multiple products in a single query.
     * Returns a map of productId -> currentStock; products with no movements map to 0.
     */
    public Map<Long, Integer> getStocksForProducts(List<Long> productIds) {
        try {
            return stockMovementRepository.computeCurrentStocks(productIds);
        } catch (SQLException e) {
            throw new RuntimeException("Error al calcular stock de productos", e);
        }
    }

    /**
     * Checks if a product's earliest lot is expired.
     * Uses purchase_items.expiry_date to determine expiry.
     *
     * @param expiryDateStr the expiry date in DD/MM/yyyy format
     * @return true if the product is expired (expiry date is in the past)
     */
    public boolean isExpired(String expiryDateStr) {
        if (expiryDateStr == null || expiryDateStr.isEmpty()) {
            return false;
        }
        try {
            LocalDate expiryDate = LocalDate.parse(expiryDateStr, DATE_FORMATTER);
            return expiryDate.isBefore(LocalDate.now());
        } catch (Exception e) {
            // A malformed date is treated as NOT expired (safe default for sales).
            // Logged at FINE so the bad value is observable without flooding production logs.
            LOGGER.log(Level.FINE, "Invalid expiry date '" + expiryDateStr + "'; treating as not expired", e);
            return false;
        }
    }

    /**
     * Registers a stock adjustment (positive for add, negative for remove).
     * Creates a StockMovement with type ADJUSTMENT (add) or EXIT (remove).
     *
     * @param productId        the product to adjust
     * @param quantityDifference positive = add stock, negative = remove stock
     * @param reason           required reason for the adjustment
     */
    public void adjustStock(Long productId, int quantityDifference, String reason) {
        StockMovement movement = new StockMovement();
        movement.setProductId(productId);
        movement.setReferenceType("ADJUSTMENT");
        movement.setNotes(reason);

        int removeQty = 0;
        if (quantityDifference >= 0) {
            movement.setMovementType("ADJUSTMENT");
            movement.setQuantity(quantityDifference);
        } else {
            removeQty = Math.abs(quantityDifference);
            movement.setMovementType("EXIT");
            movement.setQuantity(removeQty);
        }

        Connection conn = stockMovementRepository.getConnection();
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            if (quantityDifference < 0) {
                int currentStock = stockMovementRepository.computeCurrentStock(conn, productId);
                if (removeQty > currentStock) {
                    throw new IllegalArgumentException(
                            "Stock insuficiente. Stock actual: " + currentStock +
                            ", intentó quitar: " + removeQty
                    );
                }
            }
            stockMovementRepository.insert(conn, movement);
            productRepository.updateStock(conn, productId, quantityDifference);
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                // Best-effort rollback; the original failure is the one surfaced.
                LOGGER.log(Level.FINE, "Rollback falló al ajustar stock", rollbackEx);
            }
            throw new RuntimeException("Error al registrar ajuste de stock", e);
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                // Best-effort restore; the connection is owned by the caller.
                LOGGER.log(Level.FINE, "No se pudo restaurar autoCommit al ajustar stock", e);
            }
        }
    }

    /**
     * Returns movement history for a product with optional filters.
     *
     * @param productId filter by product (null = all)
     * @param type      filter by movement type (null/empty = all)
     * @param fromDate  filter from date in dd/MM/yyyy (null = no lower bound)
     * @param toDate    filter to date in dd/MM/yyyy (null = no upper bound)
     */
    public List<StockMovement> getMovementHistory(Long productId, String type, String fromDate, String toDate) {
        try {
            return stockMovementRepository.findByFilters(productId, type, fromDate, toDate);
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener historial de movimientos", e);
        }
    }

    /**
     * Checks if a product's lot is expiring within the warning window (7 days).
     *
     * @param expiryDateStr the expiry date in DD/MM/yyyy format
     * @return true if expiring within 7 days (but not yet expired)
     */
    public boolean isExpiringSoon(String expiryDateStr) {
        if (expiryDateStr == null || expiryDateStr.isEmpty()) {
            return false;
        }
        try {
            LocalDate expiryDate = LocalDate.parse(expiryDateStr, DATE_FORMATTER);
            return StockRisk.isExpiringSoon(expiryDate, LocalDate.now());
        } catch (Exception e) {
            // A malformed date produces no "expiring soon" alert (safe default).
            // Logged at FINE so the bad value is observable without flooding production logs.
            LOGGER.log(Level.FINE, "Invalid expiry date '" + expiryDateStr + "'; no expiry warning", e);
            return false;
        }
    }

    /**
     * Returns all active products that are expired or expiring soon based on purchase lot expiry dates.
     * This is a simplified check — in a real system, we'd query purchase_items.
     * Here we check via the product's stock status and use a mock-friendly approach.
     *
     * @param expiryDate the expiry date string to check (DD/MM/yyyy)
     * @return true if the product needs an expiry alert
     */
    public boolean needsExpiryAlert(String expiryDate) {
        return isExpired(expiryDate) || isExpiringSoon(expiryDate);
    }
}
