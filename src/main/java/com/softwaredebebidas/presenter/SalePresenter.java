package com.softwaredebebidas.presenter;

import com.softwaredebebidas.model.Customer;
import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.repository.CustomerRepository;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.service.SalesService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Presenter for the Point of Sale module.
 * Handles POS flow, product quick-search, barcode scan, item management, checkout.
 */
public class SalePresenter {

    private final SalesService salesService;
    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    private final List<SaleItem> cartItems = new ArrayList<>();
    private String currentChannel = "IN";
    private String currentPaymentMethod = "CASH";
    private Long selectedCustomerId;
    private double saleDiscount;
    private String saleDiscountType = "NONE";

    public SalePresenter(SalesService salesService,
                         InventoryService inventoryService,
                         ProductRepository productRepository,
                         CustomerRepository customerRepository) {
        this.salesService = salesService;
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * Searches products by name for quick search in POS.
     * Returns only active products.
     */
    public List<Product> searchProducts(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return productRepository.findAllActive();
            }
            return productRepository.searchByName(query.trim());
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar productos", e);
        }
    }

    /**
     * Finds a product by barcode for barcode scan entry.
     *
     * @return the product if found, null otherwise
     */
    public Product findByBarcode(String barcode) {
        try {
            List<Product> results = productRepository.searchByBarcode(barcode);
            return results.isEmpty() ? null : results.get(0);
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar producto por código de barras", e);
        }
    }

    /**
     * Adds a product to the cart with given quantity.
     * Uses the appropriate price based on the sales channel.
     * Validates stock before adding.
     *
     * @return true if added successfully
     * @throws SalesService.ValidationException if stock is insufficient
     */
    public boolean addToCart(Product product, int quantity) {
        return addToCart(product, quantity, currentChannel);
    }

    /**
     * Adds a product to the cart with given quantity and channel-specific pricing.
     * Validates stock before adding.
     *
     * @return true if added successfully
     * @throws SalesService.ValidationException if stock is insufficient
     */
    public boolean addToCart(Product product, int quantity, String channel) {
        if (product == null || quantity <= 0) {
            return false;
        }

        if (!inventoryService.validateStock(product.getId(), quantity)) {
            int available = inventoryService.getAvailableStock(product.getId());
            throw new SalesService.ValidationException(
                    "Stock insuficiente para " + product.getName() + ". Disponible: " + available + ", solicitado: " + quantity);
        }

        // Determine price based on channel
        double unitPrice = "PEDIDOSYA".equals(channel) && product.getPedidosyaPrice() > 0
                ? product.getPedidosyaPrice()
                : product.getSalePrice();

        // Check if product already in cart — increase quantity
        for (SaleItem item : cartItems) {
            if (item.getProductId().equals(product.getId())) {
                int newQty = item.getQuantity() + quantity;
                if (!inventoryService.validateStock(product.getId(), newQty)) {
                    int available = inventoryService.getAvailableStock(product.getId());
                    throw new SalesService.ValidationException(
                            "Stock insuficiente para " + product.getName() + ". Disponible: " + available + ", solicitado: " + newQty);
                }
                item.setQuantity(newQty);
                item.setSubtotal(newQty * item.getUnitPrice());
                return true;
            }
        }

        SaleItem item = new SaleItem();
        item.setProductId(product.getId());
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setSubtotal(quantity * unitPrice);
        cartItems.add(item);
        return true;
    }

    /**
     * Returns available stock for a product.
     */
    public int getAvailableStock(Long productId) {
        return inventoryService.getAvailableStock(productId);
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
     * Returns the product name for a given product ID.
     */
    public String getProductName(Long productId) {
        try {
            return productRepository.findById(productId)
                    .map(Product::getName)
                    .orElse("Producto #" + productId);
        } catch (SQLException e) {
            return "Producto #" + productId;
        }
    }

    /**
     * Returns current stock for a product for display in products table.
     */
    public int getProductStock(Long productId) {
        return inventoryService.getCurrentStock(productId);
    }

    /**
     * Removes an item from the cart by product ID.
     */
    public void removeFromCart(Long productId) {
        cartItems.removeIf(item -> item.getProductId().equals(productId));
    }

    /**
     * Updates quantity of a cart item.
     *
     * @throws SalesService.ValidationException if stock is insufficient for the requested quantity
     */
    public boolean updateCartQuantity(Long productId, int newQuantity) {
        if (newQuantity <= 0) {
            removeFromCart(productId);
            return true;
        }

        for (SaleItem item : cartItems) {
            if (item.getProductId().equals(productId)) {
                if (!inventoryService.validateStock(productId, newQuantity)) {
                    int available = inventoryService.getAvailableStock(productId);
                    String productName = getProductName(productId);
                    throw new SalesService.ValidationException(
                            "Stock insuficiente para " + productName + ". Disponible: " + available + ", solicitado: " + newQuantity);
                }
                item.setQuantity(newQuantity);
                item.setSubtotal(newQuantity * item.getUnitPrice());
                return true;
            }
        }
        return false;
    }


    /**
     * Returns current cart items.
     */
    public List<SaleItem> getCartItems() {
        return new ArrayList<>(cartItems);
    }

    /**
     * Calculates cart total.
     */
    public double getCartTotal() {
        return cartItems.stream()
                .mapToDouble(SaleItem::getSubtotal)
                .sum();
    }

    /**
     * Sets the sale channel (IN or PEDIDOSYA).
     */
    public void setChannel(String channel) {
        this.currentChannel = channel;
    }

    /**
     * Sets the payment method.
     */
    public void setPaymentMethod(String paymentMethod) {
        this.currentPaymentMethod = paymentMethod;
    }

    /**
     * Returns the current channel.
     */
    public String getCurrentChannel() {
        return currentChannel;
    }

    /**
     * Returns the current payment method.
     */
    public String getCurrentPaymentMethod() {
        return currentPaymentMethod;
    }

    /**
     * Searches customers by name.
     */
    public List<Customer> searchCustomers(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return customerRepository.findAll();
            }
            return customerRepository.searchByName(query.trim());
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar clientes", e);
        }
    }

    /**
     * Sets the selected customer.
     */
    public void setCustomer(Long customerId) {
        this.selectedCustomerId = customerId;
    }

    /**
     * Returns the selected customer ID.
     */
    public Long getSelectedCustomer() {
        return selectedCustomerId;
    }

    /**
     * Clears the selected customer.
     */
    public void clearCustomer() {
        this.selectedCustomerId = null;
    }

    /**
     * Sets a sale-level discount.
     *
     * @param discount discount value (percentage)
     * @param type discount type (NONE, PERCENTAGE, FIXED)
     */
    public void setSaleDiscount(double discount, String type) {
        this.saleDiscount = discount;
        this.saleDiscountType = type != null ? type : "NONE";
    }

    /**
     * Returns the current sale-level discount.
     */
    public double getSaleDiscount() {
        return saleDiscount;
    }

    /**
     * Returns the current sale-level discount type.
     */
    public String getSaleDiscountType() {
        return saleDiscountType;
    }

    /**
     * Calculates cart total with discounts applied.
     *
     * <p>Validates the discount before applying it (REQ-DISC-03 / REQ-DISC-04):
     * a negative discount, a PERCENTAGE above 100, or a FIXED discount above the
     * subtotal throws a {@link SalesService.ValidationException} instead of being
     * silently clamped. The Math.max floor remains as defense-in-depth and is
     * observable at the valid boundaries (100% → 0, FIXED == subtotal → 0).</p>
     */
    public double getDiscountedTotal() {
        double total = getCartTotal();
        if ("PERCENTAGE".equals(saleDiscountType)) {
            if (saleDiscount < 0) {
                throw new SalesService.ValidationException("El descuento no puede ser negativo.");
            }
            if (saleDiscount > 100) {
                throw new SalesService.ValidationException("El descuento porcentual no puede superar el 100%.");
            }
            return Math.max(0, total * (1 - saleDiscount / 100));
        }
        if ("FIXED".equals(saleDiscountType)) {
            if (saleDiscount < 0) {
                throw new SalesService.ValidationException("El descuento no puede ser negativo.");
            }
            if (saleDiscount > total) {
                throw new SalesService.ValidationException("El descuento fijo no puede superar el subtotal de la venta.");
            }
            return Math.max(0, total - saleDiscount);
        }
        return total;
    }

    /**
     * Completes the sale: creates sale record, decrements stock, generates receipt.
     *
     * @return the receipt text, or null if sale failed
     */
    public String completeSale() {
        if (cartItems.isEmpty()) {
            return null;
        }

        // Revalidate stock right before persisting: the cart may have been built
        // earlier and stock could have changed since addToCart. The single-instance
        // file lock makes this a defense-in-depth check today, but it becomes the
        // correctness boundary if the app ever scales to multiple terminals.
        for (SaleItem item : cartItems) {
            if (!inventoryService.validateStock(item.getProductId(), item.getQuantity())) {
                return null;
            }
        }

        Sale sale = new Sale();
        sale.setChannel(currentChannel);
        sale.setPaymentMethod(currentPaymentMethod);
        sale.setCustomerId(selectedCustomerId);
        sale.setDiscount(saleDiscount);
        sale.setDiscountType(saleDiscountType);

        List<SaleItem> itemsCopy = new ArrayList<>(cartItems);

        salesService.createSale(sale, itemsCopy);
        String receipt = salesService.generateReceipt(sale, itemsCopy);
        clearCart();

        return receipt;
    }

    /**
     * Retrieves the saved receipt text for a given sale by ID.
     *
     * @param saleId the sale ID
     * @return the receipt text, or null if not found
     */
    public String getLastReceipt(Long saleId) {
        return salesService.getReceiptText(saleId);
    }

    /**
     * Clears all cart state including selections.
     */
    public void clearCart() {
        cartItems.clear();
        selectedCustomerId = null;
        saleDiscount = 0;
        saleDiscountType = "NONE";
    }
}
