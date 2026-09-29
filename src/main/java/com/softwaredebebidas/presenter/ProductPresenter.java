package com.softwaredebebidas.presenter;

import com.softwaredebebidas.model.Category;
import com.softwaredebebidas.model.PriceTarget;
import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Subcategory;
import com.softwaredebebidas.model.Supplier;
import com.softwaredebebidas.repository.CategoryRepository;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SubcategoryRepository;
import com.softwaredebebidas.repository.SupplierRepository;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.util.AlertService;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Presenter for the Product Catalog module.
 * Handles CRUD logic, search, validation, and supplier dropdown.
 */
public class ProductPresenter {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryService inventoryService;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;

    public ProductPresenter(ProductRepository productRepository, SupplierRepository supplierRepository,
                            InventoryService inventoryService, CategoryRepository categoryRepository,
                            SubcategoryRepository subcategoryRepository) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.inventoryService = inventoryService;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
    }

    /**
     * Backward-compatible constructor used while the catalog controller is still
     * wired without hierarchy repos. Hierarchy list methods require the 5-arg
     * constructor; the catalog switches over when the cascade pickers land (WU4).
     */
    public ProductPresenter(ProductRepository productRepository, SupplierRepository supplierRepository,
                            InventoryService inventoryService) {
        this(productRepository, supplierRepository, inventoryService, null, null);
    }

    /**
     * Returns current stock for a product.
     */
    public int getCurrentStock(Long productId) {
        return inventoryService.getCurrentStock(productId);
    }

    /**
     * Returns current stock for multiple products in a single query.
     *
     * @return map of productId -> currentStock; products with no movements map to 0
     */
    public Map<Long, Integer> getStockForProducts(List<Long> productIds) {
        return inventoryService.getStocksForProducts(productIds);
    }

    /**
     * Returns stock status string (OK, LOW, OUT) for a product.
     */
    public String getStockStatus(Long productId) {
        return inventoryService.getStockStatus(productId);
    }

    /**
     * Loads all active products from the repository.
     */
    public List<Product> loadProducts() {
        try {
            return productRepository.findAllActive();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar productos", e);
        }
    }

    /**
     * Searches products by name (case-insensitive).
     */
    public List<Product> searchByName(String name) {
        try {
            return productRepository.searchByName(name);
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar productos", e);
        }
    }

    /**
     * Validates and saves a new product.
     *
     * <p>A product with {@code salePrice == 0} requires two sequential
     * confirmation dialogs (REQ-ZERO-PRICE-01); cancelling either aborts the
     * save before any repository interaction.
     *
     * @return true if saved successfully, false if validation failed or the
     *         user cancelled a zero-price confirmation
     */
    public boolean saveProduct(Product product) {
        if (!validateProduct(product)) {
            return false;
        }
        if (product.getCategory() == null) {
            product.setCategory("");
        }
        if (!confirmZeroPriceIfNeeded(product)) {
            return false;
        }
        try {
            checkBarcodeDuplicate(product.getBarcode(), null);
            productRepository.save(product);
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar producto", e);
        }
    }

    /**
     * Updates an existing product.
     *
     * <p>A product whose {@code salePrice == 0} requires two sequential
     * confirmation dialogs (REQ-ZERO-PRICE-02); cancelling either aborts the
     * update before any repository interaction.
     *
     * @return true if updated successfully, false if the user cancelled a
     *         zero-price confirmation
     */
    public boolean updateProduct(Product product) {
        if (product.getCategory() == null) {
            product.setCategory("");
        }
        if (!confirmZeroPriceIfNeeded(product)) {
            return false;
        }
        try {
            checkBarcodeDuplicate(product.getBarcode(), product.getId());
            productRepository.update(product);
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar producto", e);
        }
    }

    /**
     * Shows the two sequential zero-price confirmation dialogs when the sale
     * price is zero. The dialogs carry the technical warning texts mandated by
     * the spec: dialog 1 warns that sales generate no revenue, dialog 2 that
     * the product is sold at no cost.
     *
     * <p>Hooked here (not in {@link #validateProduct(Product)}) because
     * {@code validateProduct} is called multiple times along the save path and
     * would otherwise show the dialogs repeatedly.
     *
     * @return true when the price is non-zero or both dialogs are confirmed
     */
    private boolean confirmZeroPriceIfNeeded(Product product) {
        if (product.getSalePrice() != 0) {
            return true;
        }
        boolean firstConfirmed = AlertService.showConfirmDialog(
                "Precio en Cero",
                "Sale price is zero — sales will generate no revenue. Are you sure?");
        if (!firstConfirmed) {
            return false;
        }
        return AlertService.showConfirmDialog(
                "Precio en Cero",
                "This product will be sold at no cost. Confirm zero price?");
    }

    /**
     * Checks if a barcode is already in use by another product.
     *
     * @throws RuntimeException with specific message if duplicate exists
     */
    private void checkBarcodeDuplicate(String barcode, Long excludeProductId) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return;
        }
        try {
            Optional<Product> existing = productRepository.findByBarcodeExact(barcode);
            existing.ifPresent(existingProduct -> {
                if (excludeProductId == null || !existingProduct.getId().equals(excludeProductId)) {
                    throw new RuntimeException("Ya existe un producto con ese código de barras");
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error al verificar código de barras", e);
        }
    }

    /**
     * Deactivates a product (soft delete).
     */
    public void deactivateProduct(Long productId) {
        try {
            productRepository.deactivate(productId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al desactivar producto", e);
        }
    }

    /**
     * Safely deactivates a product only if it has no purchase/sale history.
     *
     * @return true if deactivated, false if has history (cannot deactivate)
     */
    public boolean safeDeactivateProduct(Long productId) {
        try {
            if (productRepository.hasHistory(productId)) {
                return false;
            }
            productRepository.deactivate(productId);
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Error al desactivar producto", e);
        }
    }

    /**
     * Returns the distinct categories of active products for filter/dropdown use.
     */
    public List<String> getDistinctCategories() {
        try {
            return productRepository.findDistinctCategories();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar categorías", e);
        }
    }

    /**
     * Returns the active categories for the cascade picker.
     */
    public List<Category> getCategories() {
        try {
            return categoryRepository.findAllActive();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar categorías", e);
        }
    }

    /**
     * Returns the active subcategories of a category for the cascade picker.
     */
    public List<Subcategory> getSubcategoriesForCategory(long categoryId) {
        try {
            return subcategoryRepository.findAllActiveByCategoryId(categoryId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar subcategorías", e);
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
     * Returns the active products of a category (for bulk price scoping).
     */
    public List<Product> getProductsByCategory(long categoryId) {
        try {
            return productRepository.findByCategoryId(categoryId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar productos", e);
        }
    }

    /**
     * Returns the active products of a subcategory (for bulk price scoping).
     */
    public List<Product> getProductsBySubcategory(long subcategoryId) {
        try {
            return productRepository.findBySubcategoryId(subcategoryId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar productos", e);
        }
    }

    /**
     * Returns the active products of a supplier (for bulk price scoping).
     */
    public List<Product> getProductsBySupplier(long supplierId) {
        try {
            return productRepository.findBySupplierId(supplierId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar productos", e);
        }
    }

    /**
     * Applies a percentage change to the local and/or PedidosYa prices of the
     * given products. Positive percentages raise prices, negative ones lower
     * them. Only active products are actually updated by the repository.
     *
     * <p>Guard: a LOCAL or BOTH update is rejected when any resulting local sale
     * price would fall below the product's cost price. PedidosYa has no such
     * constraint.
     *
     * @param productIds products to update
     * @param target     which price(s) to adjust
     * @param percentage percentage change (must not be zero)
     * @return number of products updated
     */
    public int applyBulkPriceUpdate(List<Long> productIds, PriceTarget target, double percentage) {
        if (percentage == 0) {
            throw new RuntimeException("El porcentaje debe ser distinto de cero");
        }
        double multiplier = 1 + percentage / 100.0;
        try {
            if (target == PriceTarget.LOCAL || target == PriceTarget.BOTH) {
                for (Product product : productRepository.findAllByIds(productIds)) {
                    if (!product.isActive()) {
                        continue;
                    }
                    double newSalePrice = Math.round(product.getSalePrice() * multiplier * 100.0) / 100.0;
                    if (newSalePrice < product.getCostPrice()) {
                        throw new RuntimeException("No se puede aplicar: hay productos que quedarían por debajo del costo");
                    }
                }
            }
            boolean applySale = target == PriceTarget.LOCAL || target == PriceTarget.BOTH;
            boolean applyPedidosya = target == PriceTarget.PEDIDOSYA || target == PriceTarget.BOTH;
            return productRepository.bulkUpdatePrices(productIds, applySale, applyPedidosya, multiplier, multiplier);
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar precios", e);
        }
    }

    /**
     * Validates product fields before save.
     */
    public boolean validateProduct(Product product) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            return false;
        }
        if (product.getPresentation() == null || product.getPresentation().trim().isEmpty()) {
            return false;
        }
        if (product.getCostPrice() < 0) {
            return false;
        }
        if (product.getSalePrice() < product.getCostPrice()) {
            return false;
        }
        if (product.getPedidosyaPrice() < 0) {
            return false;
        }
        return true;
    }
}
