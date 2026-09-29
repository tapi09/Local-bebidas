package com.softwaredebebidas.presenter;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.StockMovement;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.util.StockRisk;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Presenter for the Stock Management module.
 * Handles inventory dashboard, stock status indicators, movement log, manual adjustments.
 */
public class StockPresenter {

    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    public StockPresenter(InventoryService inventoryService,
                          ProductRepository productRepository,
                          StockMovementRepository stockMovementRepository) {
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    /**
     * Returns all active products with their current stock and status.
     */
    public List<ProductStockInfo> getDashboardData() {
        try {
            List<Product> products = productRepository.findAllActive();
            if (products == null || products.isEmpty()) {
                return java.util.Collections.emptyList();
            }

            List<Long> productIds = products.stream().map(Product::getId).toList();
            java.util.Map<Long, Integer> stockMap = stockMovementRepository.computeCurrentStocks(productIds);

            List<ProductStockInfo> dashboard = new ArrayList<>(products.size());
            for (Product product : products) {
                int stock = stockMap.getOrDefault(product.getId(), 0);
                String status = (stock == 0) ? "OUT" : (StockRisk.isAtOrBelowMinimum(stock, product.getMinStock()) ? "LOW" : "OK");
                dashboard.add(new ProductStockInfo(product, stock, status));
            }

            return dashboard;
        } catch (Exception e) {
            throw new RuntimeException("Error al cargar dashboard de stock", e);
        }
    }

    /**
     * Returns current stock for a single product (lightweight, single query).
     * Used by the product selector instead of re-running the full dashboard.
     */
    public int getStockByProduct(Long productId) {
        try {
            return stockMovementRepository.computeCurrentStock(productId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar stock del producto", e);
        }
    }

    /**
     * Returns products filtered by stock status.
     */
    public List<ProductStockInfo> getDashboardByStatus(String status) {
        return getDashboardData().stream()
                .filter(info -> info.getStatus().equals(status))
                .toList();
    }

    /**
     * Returns stock movement history for a product.
     */
    public List<StockMovement> getMovementHistory(Long productId) {
        try {
            return stockMovementRepository.findByProductId(productId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar historial de movimientos", e);
        }
    }

    /**
     * Returns stock movement history filtered by type.
     */
    public List<StockMovement> getMovementHistoryByType(Long productId, String movementType) {
        try {
            return stockMovementRepository.findByProductIdAndType(productId, movementType);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar movimientos", e);
        }
    }

    /**
     * Returns movement history with optional filters (product, type, date range).
     */
    public List<StockMovement> getMovementHistory(Long productId, String type, String fromDate, String toDate) {
        try {
            return stockMovementRepository.findByFilters(productId, type, fromDate, toDate);
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener historial de movimientos", e);
        }
    }

    /**
     * Adjusts stock for a product with a signed difference and reason.
     * Delegates to InventoryService.
     */
    public void adjustStock(Long productId, int difference, String reason) {
        inventoryService.adjustStock(productId, difference, reason);
    }

    /**
     * Returns all active products for selection in the UI.
     */
    public List<Product> getAllProducts() {
        try {
            return productRepository.findAllActive();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar productos", e);
        }
    }

    /**
     * Creates a manual stock adjustment.
     *
     * @param productId the product to adjust
     * @param quantity  the adjustment quantity (always positive)
     * @param direction IN (add) or OUT (subtract)
     * @param reason    required reason for the adjustment
     * @return true if adjustment was created successfully
     */
    public boolean createAdjustment(Long productId, int quantity, String direction, String reason) {
        if (quantity <= 0) {
            return false;
        }
        if (reason == null || reason.trim().isEmpty()) {
            return false;
        }
        if (!"IN".equals(direction) && !"OUT".equals(direction)) {
            return false;
        }

        try {
            StockMovement movement = new StockMovement();
            movement.setProductId(productId);
            movement.setMovementType("OUT".equals(direction) ? "EXIT" : "ADJUSTMENT");
            movement.setQuantity(quantity);
            movement.setReferenceType("ADJUSTMENT");
            movement.setNotes(reason.trim());
            stockMovementRepository.insert(movement);

            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Error al crear ajuste de stock", e);
        }
    }

    /**
     * Returns low stock products.
     */
    public List<Product> getLowStockProducts() {
        return inventoryService.getLowStockProducts();
    }

    /**
     * Returns the count of products in each status category.
     */
    public StockStatusCounts getStatusCounts() {
        List<ProductStockInfo> data = getDashboardData();
        int ok = 0, low = 0, out = 0;
        for (ProductStockInfo info : data) {
            switch (info.getStatus()) {
                case "OK" -> ok++;
                case "LOW" -> low++;
                case "OUT" -> out++;
            }
        }
        return new StockStatusCounts(ok, low, out);
    }

    /**
     * Inner class to hold product stock info for the dashboard.
     */
    public static class ProductStockInfo {
        private final Product product;
        private final int currentStock;
        private final String status;

        public ProductStockInfo(Product product, int currentStock, String status) {
            this.product = product;
            this.currentStock = currentStock;
            this.status = status;
        }

        public Product getProduct() { return product; }
        public int getCurrentStock() { return currentStock; }
        public String getStatus() { return status; }
        public String getProductName() { return product.getName(); }
        public String getProductCategory() { return product.getHierarchyLabel(); }
        public int getMinStock() { return product.getMinStock(); }
    }

    /**
     * Inner class to hold status counts.
     */
    public static class StockStatusCounts {
        private final int ok;
        private final int low;
        private final int out;

        public StockStatusCounts(int ok, int low, int out) {
            this.ok = ok;
            this.low = low;
            this.out = out;
        }

        public int getOk() { return ok; }
        public int getLow() { return low; }
        public int getOut() { return out; }
        public int getTotal() { return ok + low + out; }
    }
}
