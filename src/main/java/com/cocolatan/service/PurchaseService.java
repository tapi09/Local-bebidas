package com.cocolatan.service;

import com.cocolatan.model.Product;
import com.cocolatan.model.Purchase;
import com.cocolatan.model.PurchaseItem;
import com.cocolatan.model.StockMovement;
import com.cocolatan.model.Supplier;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.PurchaseRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.repository.SupplierRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Service for purchase operations: purchase creation, stock movements,
 * cost price updates, and purchase history queries.
 * <p>
 * Orchestrates the full purchase flow inside a single SQL transaction.
 */
public class PurchaseService {

    private final DatabaseManager databaseManager;
    private final PurchaseRepository purchaseRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    public PurchaseService(PurchaseRepository purchaseRepository,
                           StockMovementRepository stockMovementRepository,
                           ProductRepository productRepository,
                           SupplierRepository supplierRepository,
                           DatabaseManager databaseManager) {
        this.purchaseRepository = purchaseRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.databaseManager = databaseManager;
    }

    /**
     * Saves a purchase with its items. Creates stock movements and updates cost prices.
     * The entire operation (purchase + stock movements + cost price update) is wrapped
     * in a single SQL transaction for atomicity.
     *
     * @return true if saved successfully, false if validation failed
     */
    public boolean savePurchase(Purchase purchase, List<PurchaseItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }

        Connection conn = databaseManager.getConnection();
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            Long purchaseId = purchaseRepository.saveWithItems(conn, purchase, items);

            // Create stock movements for each item
            for (PurchaseItem item : items) {
                StockMovement movement = new StockMovement();
                movement.setProductId(item.getProductId());
                movement.setMovementType("ENTRY");
                movement.setQuantity(item.getQuantity());
                movement.setReferenceType("PURCHASE");
                movement.setReferenceId(purchaseId);
                movement.setNotes("Compra #" + purchaseId);
                stockMovementRepository.insert(conn, movement);

                // Update product cost price
                productRepository.updateCostPrice(conn, item.getProductId(), item.getUnitCost());
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
            throw new RuntimeException("Error al guardar compra", e);
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException e) { /* ignore */ }
        }
    }

    /**
     * Loads purchase history sorted by date descending.
     */
    public List<Purchase> loadPurchaseHistory() {
        try {
            return purchaseRepository.findHistory();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar historial de compras", e);
        }
    }

    /**
     * Loads suppliers for dropdown selection.
     */
    public List<Supplier> loadSuppliers() {
        try {
            return supplierRepository.findForDropdown();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar proveedores", e);
        }
    }

    /**
     * Returns supplier name by ID, or "Proveedor #ID" if not found.
     */
    public String getSupplierName(Long supplierId) {
        try {
            return supplierRepository.findById(supplierId)
                    .map(Supplier::getName)
                    .orElse("Proveedor #" + supplierId);
        } catch (SQLException e) {
            return "Proveedor #" + supplierId;
        }
    }

    /**
     * Returns product by ID.
     */
    public Product getProductById(Long productId) {
        try {
            return productRepository.findById(productId).orElse(null);
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener producto", e);
        }
    }

    /**
     * Returns product for dropdown with all needed fields for display.
     */
    public List<Product> loadProductsForDropdown() {
        try {
            return productRepository.findAllActive();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar productos para dropdown", e);
        }
    }

    /**
     * Saves a new supplier and returns the complete Supplier object.
     */
    public Supplier saveSupplier(Supplier supplier) {
        try {
            Long savedId = supplierRepository.save(supplier);
            supplier.setId(savedId);
            return supplier;
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar proveedor", e);
        }
    }

    /**
     * Saves a new product and returns the complete Product object with its generated ID.
     */
    public Product saveProduct(Product product) {
        try {
            Long savedId = productRepository.save(product);
            product.setId(savedId);
            return product;
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar producto", e);
        }
    }

    /**
     * Returns purchase items for a given purchase ID.
     */
    public List<PurchaseItem> getPurchaseItems(Long purchaseId) {
        try {
            return purchaseRepository.findItemsByPurchaseId(purchaseId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener ítems de compra", e);
        }
    }
}
