package com.cocolatan.presenter;

import com.cocolatan.model.Product;
import com.cocolatan.model.Purchase;
import com.cocolatan.model.PurchaseItem;
import com.cocolatan.model.Supplier;
import com.cocolatan.service.PurchaseService;

import java.util.List;

/**
 * Presenter for the Purchase Entry module.
 * Handles purchase creation, item management, stock auto-increment, and cost price updates.
 * All persistence and transactional operations are delegated to {@link PurchaseService}.
 */
public class PurchasePresenter {

    private final PurchaseService purchaseService;

    public PurchasePresenter(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    /**
     * Calculates the subtotal from a list of purchase items.
     *
     * @return the sum of (quantity * unitCost) for all items
     */
    public double calculateSubtotal(List<PurchaseItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        return items.stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitCost())
                .sum();
    }

    /**
     * Returns the total for a purchase: subtotal + tax.
     */
    public double calculateTotal(double subtotal, double taxAmount) {
        return subtotal + taxAmount;
    }

    /**
     * Saves a purchase with its items atomically.
     *
     * @return true if saved successfully, false if validation failed
     */
    public boolean savePurchase(Purchase purchase, List<PurchaseItem> items) {
        return purchaseService.savePurchase(purchase, items);
    }

    /**
     * Loads purchase history sorted by date descending.
     */
    public List<Purchase> loadPurchaseHistory() {
        return purchaseService.loadPurchaseHistory();
    }

    /**
     * Loads suppliers for dropdown selection.
     */
    public List<Supplier> loadSuppliers() {
        return purchaseService.loadSuppliers();
    }

    /**
     * Returns supplier name by ID, or "Proveedor #ID" if not found.
     */
    public String getSupplierName(Long supplierId) {
        return purchaseService.getSupplierName(supplierId);
    }

    /**
     * Returns product by ID.
     */
    public Product getProductById(Long productId) {
        return purchaseService.getProductById(productId);
    }

    /**
     * Returns product for dropdown with all needed fields for display.
     */
    public List<Product> loadProductsForDropdown() {
        return purchaseService.loadProductsForDropdown();
    }

    /**
     * Saves a new supplier and returns the complete Supplier object.
     */
    public Supplier saveSupplier(Supplier supplier) {
        return purchaseService.saveSupplier(supplier);
    }

    /**
     * Saves a new product and returns the complete Product object with its generated ID.
     */
    public Product saveProduct(Product product) {
        return purchaseService.saveProduct(product);
    }

    /**
     * Returns purchase items for a given purchase ID.
     */
    public List<PurchaseItem> getPurchaseItems(Long purchaseId) {
        return purchaseService.getPurchaseItems(purchaseId);
    }
}
