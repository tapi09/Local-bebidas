package com.cocolatan.view;

import com.cocolatan.model.Customer;
import com.cocolatan.model.Product;
import com.cocolatan.model.SaleItem;
import com.cocolatan.presenter.SalePresenter;
import com.cocolatan.repository.CustomerRepository;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.SaleRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.service.InventoryService;
import com.cocolatan.service.ReceiptService;
import com.cocolatan.service.SalesService;
import com.cocolatan.util.AlertService;
import com.cocolatan.util.CurrencyFormatter;
import com.cocolatan.util.PhotoUtils;
import com.cocolatan.util.Refreshable;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Point of Sale view.
 * Delegates business logic to SalePresenter.
 */
public class SaleController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(SaleController.class.getName());

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Product> productsTable;

    @FXML
    private TableColumn<Product, String> colProdName;

    @FXML
    private TableColumn<Product, String> colProdCategory;

    @FXML
    private TableColumn<Product, Double> colProdPrice;

    @FXML
    private TableColumn<Product, Integer> colProdStock;

    @FXML
    private ImageView productPhotoPreview;

    @FXML
    private TextField quantityField;

    @FXML
    private TableView<SaleItem> cartTable;

    @FXML
    private TableColumn<SaleItem, String> colCartProduct;

    @FXML
    private TableColumn<SaleItem, Integer> colCartQty;

    @FXML
    private TableColumn<SaleItem, Double> colCartPrice;

    @FXML
    private TableColumn<SaleItem, Double> colCartSubtotal;

    @FXML
    private ComboBox<String> channelCombo;

    @FXML
    private ComboBox<String> paymentCombo;

    @FXML
    private ComboBox<Customer> customerCombo;

    @FXML
    private TextField customerSearchField;

    @FXML
    private ComboBox<String> discountCombo;

    @FXML
    private TextField discountField;

    @FXML
    private Label discountAmountLabel;

    @FXML
    private Label totalLabel;

    @FXML
    private TextField receiptField;

    private SalePresenter presenter;
    private ObservableList<Product> productsData;
    private ObservableList<SaleItem> cartData;

    // Cached product list for real-time filtering (no DB hits on every keystroke)
    private List<Product> allCachedProducts;

    // Cached stock per product for the products table (batch query per load).
    private Map<Long, Integer> stockMap = Collections.emptyMap();

    // Cached product names for the cart table (built once per product load).
    private Map<Long, String> productNameMap = Collections.emptyMap();

    @FXML
    public void initialize() {
        com.cocolatan.repository.DatabaseManager dbManager = com.cocolatan.CocolatanApp.getDatabaseManager();
        ProductRepository productRepo = new ProductRepository(dbManager);
        StockMovementRepository stockMovementRepo = new StockMovementRepository(dbManager);
        InventoryService inventoryService = new InventoryService(stockMovementRepo, productRepo);
        CustomerRepository customerRepo = new CustomerRepository(dbManager);
        presenter = new SalePresenter(
                new SalesService(
                        new SaleRepository(dbManager),
                        stockMovementRepo,
                        productRepo,
                        inventoryService,
                        new ReceiptService(productRepo),
                        dbManager
                ),
                inventoryService,
                productRepo,
                customerRepo
        );

        setupTables();
        setupCombos();
        setupProductSearch();
        loadInitialProducts();
        loadCustomers();
    }

    private void setupTables() {
        // Products table
        colProdName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        colProdCategory.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("hierarchyLabel"));
        colProdPrice.setCellValueFactory(cellData -> {
            Product p = cellData.getValue();
            double price = isPedidosYa() && p.getPedidosyaPrice() > 0
                    ? p.getPedidosyaPrice()
                    : p.getSalePrice();
            return new SimpleDoubleProperty(price).asObject();
        });
        colProdStock.setCellValueFactory(cellData -> {
            Product product = cellData.getValue();
            int stock = stockMap.getOrDefault(product.getId(), 0);
            return new javafx.beans.property.SimpleIntegerProperty(stock).asObject();
        });

        // Cart table
        colCartQty.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("quantity"));
        colCartPrice.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("unitPrice"));
        colCartSubtotal.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("subtotal"));
        // Created once; reads names from the cached map (no DB query per row).
        colCartProduct.setCellValueFactory(cellData -> {
            SaleItem item = cellData.getValue();
            String productName = productNameMap.getOrDefault(item.getProductId(), "Producto #" + item.getProductId());
            return new javafx.beans.property.SimpleStringProperty(productName);
        });

        // Update photo preview when product is selected
        productsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showProductPhoto(newVal);
        });
    }

    private void showProductPhoto(Product product) {
        if (product == null || product.getPhotoPath() == null || product.getPhotoPath().isBlank()) {
            productPhotoPreview.setImage(null);
            return;
        }
        Path resolved = PhotoUtils.resolvePath(product.getPhotoPath());
        if (resolved != null) {
            Image img = new Image(resolved.toUri().toString(), 64, 64, true, true);
            productPhotoPreview.setImage(img);
        } else {
            productPhotoPreview.setImage(null);
        }
    }

    private void setupCombos() {
        channelCombo.setItems(FXCollections.observableArrayList("Local", "PedidosYa"));
        channelCombo.getSelectionModel().selectFirst();
        channelCombo.setOnAction(e -> onChannelChanged());

        paymentCombo.setItems(FXCollections.observableArrayList(
                "Efectivo", "Tarjeta de Crédito", "Tarjeta de Débito", "Transferencia"
        ));
        paymentCombo.getSelectionModel().selectFirst();

        discountCombo.setItems(FXCollections.observableArrayList("Sin descuento", "% Descuento", "Fijo $"));
        discountCombo.getSelectionModel().selectFirst();
        discountCombo.setOnAction(e -> onDiscountChanged());
        discountField.setText("0");
    }

    private void loadCustomers() {
        // Cell renderer for customer combo
        customerCombo.setCellFactory(param -> new ListCell<Customer>() {
            @Override
            protected void updateItem(Customer customer, boolean empty) {
                super.updateItem(customer, empty);
                if (empty || customer == null) {
                    setText(null);
                } else {
                    setText(customer.getName());
                }
            }
        });
        customerCombo.setButtonCell(new ListCell<Customer>() {
            @Override
            protected void updateItem(Customer customer, boolean empty) {
                super.updateItem(customer, empty);
                setText(empty || customer == null ? "Sin cliente" : customer.getName());
            }
        });

        List<Customer> customers = presenter.searchCustomers("");
        ObservableList<Customer> customerData = FXCollections.observableArrayList(customers);
        customerCombo.setItems(customerData);
    }

    @FXML
    private void onCustomerSearch() {
        String query = customerSearchField.getText();
        List<Customer> results = presenter.searchCustomers(query);
        customerCombo.setItems(FXCollections.observableArrayList(results));
    }

    @FXML
    private void onCustomerSelected() {
        Customer selected = customerCombo.getValue();
        if (selected != null) {
            presenter.setCustomer(selected.getId());
        } else {
            presenter.clearCustomer();
        }
    }

    @FXML
    private void onDiscountChanged() {
        String type = discountCombo.getValue();
        double value = 0;
        try {
            value = Double.parseDouble(discountField.getText().trim());
        } catch (NumberFormatException e) {
            discountField.setText("0");
        }

        if ("% Descuento".equals(type) && value > 0) {
            presenter.setSaleDiscount(value, "PERCENTAGE");
        } else if ("Fijo $".equals(type) && value > 0) {
            presenter.setSaleDiscount(value, "FIXED");
        } else {
            presenter.setSaleDiscount(0, "NONE");
        }

        double discountedTotal = presenter.getDiscountedTotal();
        totalLabel.setText(CurrencyFormatter.format(discountedTotal));

        if ("PERCENTAGE".equals(presenter.getSaleDiscountType())) {
            discountAmountLabel.setText(presenter.getSaleDiscount() + "% desc.");
            discountAmountLabel.setVisible(true);
        } else if ("FIXED".equals(presenter.getSaleDiscountType())) {
            discountAmountLabel.setText("-$" + String.format("%.0f", presenter.getSaleDiscount()));
            discountAmountLabel.setVisible(true);
        } else {
            discountAmountLabel.setVisible(false);
        }
    }

    @Override
    public void refresh() {
        loadInitialProducts();
        loadCustomers();
    }

    // ──────────────────────────────────────────────
    // Channel-based pricing
    // ──────────────────────────────────────────────

    private boolean isPedidosYa() {
        return "PedidosYa".equals(channelCombo.getValue());
    }

    @FXML
    private void onChannelChanged() {
        // Refresh displayed prices in products table
        refreshStockMap();
        productsTable.refresh();
        // Also refresh cart prices to reflect channel change
        updateCartDisplay();
    }

    // ──────────────────────────────────────────────
    // Real-time product search (cached, no DB reload)
    // ──────────────────────────────────────────────

    private void setupProductSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterProducts(newVal);
        });
    }

    private void filterProducts(String query) {
        if (allCachedProducts == null) return;
        ObservableList<Product> filtered = FXCollections.observableArrayList();
        boolean emptyFilter = query == null || query.trim().isEmpty();

        for (Product p : allCachedProducts) {
            if (emptyFilter || matchesQuery(query, p)) {
                filtered.add(p);
            }
        }
        productsData = filtered;
        productsTable.setItems(productsData);
    }

    /**
     * Returns true when the product matches the typed query on name, barcode,
     * category, or subcategory using a case-insensitive substring match.
     * A null or blank query matches every product.
     */
    static boolean matchesQuery(String query, Product product) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.toLowerCase().trim();
        return product.getName().toLowerCase().contains(q)
            || (product.getBarcode() != null && product.getBarcode().toLowerCase().contains(q))
            || (product.getCategory() != null && product.getCategory().toLowerCase().contains(q))
            || (product.getSubcategoryName() != null && product.getSubcategoryName().toLowerCase().contains(q));
    }

    private void loadInitialProducts() {
        allCachedProducts = presenter.searchProducts("");
        productsData = FXCollections.observableArrayList(allCachedProducts);
        productsTable.setItems(productsData);
        refreshStockMap();
        refreshProductNameMap();
    }

    /**
     * Recomputes the cached stock map for the loaded products in a single batched
     * query. Called on load and whenever stock changes (channel change, sale done).
     */
    private void refreshStockMap() {
        if (allCachedProducts == null) {
            stockMap = Collections.emptyMap();
            return;
        }
        List<Long> ids = allCachedProducts.stream().map(Product::getId).toList();
        stockMap = presenter.getStockForProducts(ids);
    }

    /**
     * Rebuilds the product-name map from the already-loaded product list, so the
     * cart table never hits the database per row.
     */
    private void refreshProductNameMap() {
        if (allCachedProducts == null) {
            productNameMap = Collections.emptyMap();
            return;
        }
        Map<Long, String> names = new HashMap<>();
        for (Product product : allCachedProducts) {
            names.put(product.getId(), product.getName());
        }
        productNameMap = names;
    }

    @FXML
    private void onSearch() {
        // Kept for barcode flow — real-time filter already covers text search
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            filterProducts(null);
        }
    }

    @FXML
    private void onBarcodeScan() {
        String barcode = searchField.getText().trim();
        if (barcode.isEmpty()) {
            AlertService.showWarningDialog("Código de Barras", "Ingrese un código de barras.");
            return;
        }

        Product product = presenter.findByBarcode(barcode);
        if (product == null) {
            AlertService.showWarningDialog("Producto No Encontrado", "No se encontró producto con código: " + barcode);
            return;
        }

        // Auto-add to cart with quantity 1
        String channel = isPedidosYa() ? "PEDIDOSYA" : "IN";
        boolean added = presenter.addToCart(product, 1, channel);
        if (added) {
            updateCartDisplay();
            AlertService.showInfoDialog("Producto Agregado", product.getName() + " agregado al carrito.");
        } else {
            AlertService.showErrorDialog("Error", "No se pudo agregar el producto. Stock insuficiente.");
        }
    }

    @FXML
    private void onQuantityUp() {
        try {
            int qty = Integer.parseInt(quantityField.getText().trim());
            quantityField.setText(String.valueOf(qty + 1));
        } catch (NumberFormatException e) {
            quantityField.setText("1");
        }
    }

    @FXML
    private void onQuantityDown() {
        try {
            int qty = Integer.parseInt(quantityField.getText().trim());
            if (qty > 1) {
                quantityField.setText(String.valueOf(qty - 1));
            }
        } catch (NumberFormatException e) {
            quantityField.setText("1");
        }
    }

    @FXML
    private void onAddToCart() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Producto", "Seleccione un producto de la lista.");
            return;
        }

        try {
            int quantity = Integer.parseInt(quantityField.getText().trim());
            if (quantity <= 0) {
                AlertService.showErrorDialog("Error", "La cantidad debe ser mayor a 0.");
                return;
            }

            String channel = isPedidosYa() ? "PEDIDOSYA" : "IN";
            boolean added = presenter.addToCart(selected, quantity, channel);
            if (added) {
                updateCartDisplay();
                quantityField.setText("1");
            } else {
                AlertService.showErrorDialog("Stock Insuficiente",
                        "No hay stock suficiente para " + selected.getName());
            }
        } catch (NumberFormatException e) {
            AlertService.showErrorDialog("Error de Formato", "Ingrese una cantidad válida.");
        }
    }

    @FXML
    private void onRemoveFromCart() {
        SaleItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Ítem", "Seleccione un ítem del carrito.");
            return;
        }
        presenter.removeFromCart(selected.getProductId());
        updateCartDisplay();
    }

    @FXML
    private void onClearCart() {
        presenter.clearCart();
        updateCartDisplay();
    }

    @FXML
    private void onSaleHistory() {
        com.cocolatan.presenter.MainPresenter.getInstance().onSaleHistory();
    }

    @FXML
    private void onCompleteSale() {
        // Update channel and payment from combos
        String channelText = channelCombo.getValue();
        String paymentText = paymentCombo.getValue();

        if (channelText != null) {
            presenter.setChannel(channelText.equals("Local") ? "IN" : "PEDIDOSYA");
        }
        if (paymentText != null) {
            presenter.setPaymentMethod(mapPaymentMethod(paymentText));
        }

        try {
            String receipt = presenter.completeSale();
            if (receipt != null) {
                receiptField.setText(receipt);
                refreshStockMap();
                updateCartDisplay();
                AlertService.showInfoDialog("Venta Completada", "Venta registrada exitosamente.");
            } else {
                AlertService.showWarningDialog("Carrito Vacío", "Agregue productos antes de completar la venta.");
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al completar venta", e);
            AlertService.showErrorDialog("Error", e.getMessage());
        }
    }

    private String mapPaymentMethod(String displayText) {
        return switch (displayText) {
            case "Efectivo" -> "CASH";
            case "Tarjeta de Crédito" -> "CREDIT_CARD";
            case "Tarjeta de Débito" -> "DEBIT_CARD";
            case "Transferencia" -> "TRANSFER";
            default -> "CASH";
        };
    }

    private void updateCartDisplay() {
        cartData = FXCollections.observableArrayList(presenter.getCartItems());
        cartTable.setItems(cartData);

        double total = presenter.getDiscountedTotal();
        totalLabel.setText(CurrencyFormatter.format(total));
    }
}
