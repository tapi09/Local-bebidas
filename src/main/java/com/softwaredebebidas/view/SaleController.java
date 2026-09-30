package com.softwaredebebidas.view;

import com.softwaredebebidas.model.Customer;
import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.presenter.SalePresenter;
import com.softwaredebebidas.repository.CustomerRepository;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.service.ReceiptService;
import com.softwaredebebidas.service.SalesService;
import com.softwaredebebidas.util.AlertService;
import com.softwaredebebidas.util.CurrencyFormatter;
import com.softwaredebebidas.util.PhotoUtils;
import com.softwaredebebidas.util.Refreshable;
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
    private TableColumn<Product, String> colProdPresentation;

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
    private CheckBox splitPaymentCheck;

    @FXML
    private TextField splitFirstAmountField;

    @FXML
    private ComboBox<String> splitSecondPaymentCombo;

    @FXML
    private Label splitRemainderLabel;

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
        com.softwaredebebidas.repository.DatabaseManager dbManager = com.softwaredebebidas.SoftwareDeBebidasApp.getDatabaseManager();
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
                        new ReceiptService(productRepo, new com.softwaredebebidas.repository.ConfigRepository(dbManager)),
                        dbManager
                ),
                inventoryService,
                productRepo,
                customerRepo
        );

        setupTables();
        setupCombos();
        setupSplitPayment();
        setupProductSearch();
        loadInitialProducts();
        loadCustomers();
    }

    private void setupTables() {
        // Products table
        colProdName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        if (colProdPresentation != null) {
            colProdPresentation.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("presentation"));
        }
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
            if (item instanceof DiscountCartLine discountLine) {
                return new javafx.beans.property.SimpleStringProperty(discountLine.getLabel());
            }
            String productName = productNameMap.getOrDefault(item.getProductId(), "Producto #" + item.getProductId());
            return new javafx.beans.property.SimpleStringProperty(productName);
        });
        // Normal rows render exactly as before; the display-only discount row shows
        // blank quantity/price and a formatted negative amount.
        installCartCellFactory(colCartQty, value -> "");
        installCartCellFactory(colCartPrice, value -> "");
        installCartCellFactory(colCartSubtotal, CurrencyFormatter::format);

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
        // Apply the discount value on Enter and when the field loses focus (Tab/click),
        // not only when the discount type combo changes.
        // Enter also confirms the result to the user; Tab/focus-lost stays silent.
        discountField.setOnAction(e -> onDiscountEntered());
        discountField.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                onDiscountChanged();
            }
        });
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
        applyDiscount();
    }

    /**
     * Enter pressed in the discount field: applies the discount and tells the user
     * what happened. Invalid values keep the error dialog of {@link #applyDiscount()}
     * and get no extra dialog.
     */
    private void onDiscountEntered() {
        String comboBefore = discountCombo.getValue();
        boolean noTypeSelected = comboBefore == null || "Sin descuento".equals(comboBefore);
        double typed = parseDiscountValue(discountField.getText());

        if (!applyDiscount()) {
            return;
        }
        String type = presenter.getSaleDiscountType();
        if ("PERCENTAGE".equals(type) || "FIXED".equals(type)) {
            double amount = presenter.getCartTotal() - presenter.getDiscountedTotal();
            AlertService.showInfoDialog("Descuento Aplicado",
                    discountAppliedMessage(type, presenter.getSaleDiscount(), amount));
        } else if (noTypeSelected && typed > 0) {
            AlertService.showWarningDialog("Tipo de Descuento",
                    "Elegí si el descuento es porcentual (%) o un monto fijo ($).");
        }
    }

    private static double parseDiscountValue(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * Applies the discount typed in the controls to the presenter and refreshes the
     * total and the cart rows.
     *
     * @return false when the discount was invalid (error shown and discount cleared)
     */
    private boolean applyDiscount() {
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

        double discountedTotal;
        try {
            discountedTotal = presenter.getDiscountedTotal();
        } catch (SalesService.ValidationException e) {
            // Invalid discount (negative, % above 100, fixed above subtotal):
            // tell the user and reset the discount state instead of propagating.
            AlertService.showErrorDialog("Descuento Inválido", e.getMessage());
            clearDiscountState();
            totalLabel.setText(CurrencyFormatter.format(presenter.getCartTotal()));
            refreshCartRows();
            return false;
        }
        totalLabel.setText(CurrencyFormatter.format(discountedTotal));
        updateSplitRemainder();

        if ("PERCENTAGE".equals(presenter.getSaleDiscountType())) {
            discountAmountLabel.setText(presenter.getSaleDiscount() + "% desc.");
            discountAmountLabel.setVisible(true);
        } else if ("FIXED".equals(presenter.getSaleDiscountType())) {
            discountAmountLabel.setText("-$" + String.format("%.0f", presenter.getSaleDiscount()));
            discountAmountLabel.setVisible(true);
        } else {
            discountAmountLabel.setVisible(false);
        }
        refreshCartRows();
        return true;
    }

    /** Clears the discount in the presenter and resets the discount controls. */
    private void clearDiscountState() {
        presenter.setSaleDiscount(0, "NONE");
        discountCombo.getSelectionModel().selectFirst();
        discountField.setText("0");
        discountAmountLabel.setVisible(false);
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
            names.put(product.getId(), cartProductLabel(product));
        }
        productNameMap = names;
    }

    /**
     * Cart label for a product: its name plus the presentation (e.g. "quilmes (473)"),
     * so two presentations of the same product can be told apart in the cart.
     */
    static String cartProductLabel(Product product) {
        String presentation = product.getPresentation();
        if (presentation == null || presentation.isBlank()) {
            return product.getName();
        }
        return product.getName() + " (" + presentation.trim() + ")";
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
        try {
            boolean added = presenter.addToCart(product, 1, channel);
            if (added) {
                updateCartDisplay();
                AlertService.showInfoDialog("Producto Agregado", product.getName() + " agregado al carrito.");
            } else {
                AlertService.showErrorDialog("Error", "No se pudo agregar el producto. Stock insuficiente.");
            }
        } catch (SalesService.ValidationException e) {
            AlertService.showErrorDialog("Stock Insuficiente", e.getMessage());
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
        } catch (SalesService.ValidationException e) {
            AlertService.showErrorDialog("Stock Insuficiente", e.getMessage());
        }
    }

    @FXML
    private void onRemoveFromCart() {
        SaleItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Ítem", "Seleccione un ítem del carrito.");
            return;
        }
        if (selected instanceof DiscountCartLine) {
            // The discount row is display-only: removing it clears the discount itself.
            clearDiscountState();
            updateCartDisplay();
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
        com.softwaredebebidas.presenter.MainPresenter.getInstance().onSaleHistory();
    }

    /**
     * Wires the optional split-payment controls. Controls are hidden until the
     * checkbox is checked; the remainder is refreshed only from their own listeners.
     */
    private void setupSplitPayment() {
        if (splitPaymentCheck == null) {
            return;
        }
        if (splitSecondPaymentCombo != null) {
            splitSecondPaymentCombo.setItems(FXCollections.observableArrayList(
                    "Efectivo", "Tarjeta de Crédito", "Tarjeta de Débito", "Transferencia"
            ));
            splitSecondPaymentCombo.getSelectionModel().select("Transferencia");
        }
        setSplitControlsVisible(false);
        splitPaymentCheck.selectedProperty().addListener((obs, was, selected) -> {
            setSplitControlsVisible(selected);
            updateSplitRemainder();
        });
        if (splitFirstAmountField != null) {
            splitFirstAmountField.textProperty().addListener((obs, old, text) -> updateSplitRemainder());
        }
    }

    private void setSplitControlsVisible(boolean visible) {
        for (javafx.scene.Node node : new javafx.scene.Node[]{
                splitFirstAmountField, splitSecondPaymentCombo, splitRemainderLabel}) {
            if (node != null) {
                node.setVisible(visible);
                node.setManaged(visible);
            }
        }
    }

    private void updateSplitRemainder() {
        if (splitRemainderLabel == null) {
            return;
        }
        try {
            Double first = splitFirstAmountField == null ? null : parseAmount(splitFirstAmountField.getText());
            if (first == null) {
                splitRemainderLabel.setText("");
                return;
            }
            double remainder = presenter.getDiscountedTotal() - first;
            splitRemainderLabel.setText("Resto: " + CurrencyFormatter.format(remainder));
        } catch (RuntimeException e) {
            // Invalid amount or invalid discount: blank the label, the sale is validated on completion.
            splitRemainderLabel.setText("");
        }
    }

    /**
     * Parses a user-typed amount ("1500", "1500,50", "1.500,50").
     *
     * @return the amount, or null when the text is blank
     * @throws NumberFormatException if the text is not a valid amount
     */
    private static Double parseAmount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String normalized = text.trim().replace("$", "").replace(" ", "");
        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(',', '.');
        } else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) {
            normalized = normalized.replace(".", "");
        }
        return Double.parseDouble(normalized);
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

        boolean split = splitPaymentCheck != null && splitPaymentCheck.isSelected();
        if (split) {
            Double firstAmount;
            try {
                firstAmount = splitFirstAmountField == null ? null : parseAmount(splitFirstAmountField.getText());
            } catch (NumberFormatException e) {
                firstAmount = null;
            }
            if (firstAmount == null || splitSecondPaymentCombo == null || splitSecondPaymentCombo.getValue() == null) {
                AlertService.showWarningDialog("Pago Dividido", "Ingrese un monto válido para el primer pago.");
                return;
            }
            presenter.setSplitPayment(mapPaymentMethod(splitSecondPaymentCombo.getValue()), firstAmount);
        } else {
            presenter.clearSplitPayment();
        }

        try {
            String receipt = presenter.completeSale();
            if (receipt != null) {
                receiptField.setText(receipt);
                if (splitPaymentCheck != null) {
                    splitPaymentCheck.setSelected(false);
                }
                if (splitFirstAmountField != null) {
                    splitFirstAmountField.clear();
                }
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
        refreshCartRows();

        double total = presenter.getDiscountedTotal();
        totalLabel.setText(CurrencyFormatter.format(total));
        updateSplitRemainder();
    }

    /** Rebuilds the cart table rows: the presenter's items plus the display-only discount line. */
    private void refreshCartRows() {
        if (cartTable == null) {
            return;
        }
        double cartTotal = presenter.getCartTotal();
        double discountAmount = cartTotal - presenter.getDiscountedTotal();
        cartData = FXCollections.observableArrayList(cartRowsWithDiscount(
                presenter.getCartItems(), presenter.getSaleDiscountType(),
                presenter.getSaleDiscount(), discountAmount));
        cartTable.setItems(cartData);
    }

    /**
     * Returns the rows to display: {@code cartItems} plus, when a discount is active
     * (PERCENTAGE or FIXED with a positive amount), one trailing {@link DiscountCartLine}.
     * The input list is not modified.
     */
    static List<SaleItem> cartRowsWithDiscount(List<SaleItem> cartItems, String discountType,
                                               double discountValue, double discountAmount) {
        List<SaleItem> rows = new java.util.ArrayList<>(cartItems);
        boolean active = "PERCENTAGE".equals(discountType) || "FIXED".equals(discountType);
        if (active && discountAmount > 0) {
            rows.add(new DiscountCartLine(discountLineLabel(discountType, discountValue), discountAmount));
        }
        return rows;
    }

    /** Product-column text of the discount row: "Descuento 20%" or "Descuento fijo". */
    static String discountLineLabel(String discountType, double discountValue) {
        return "PERCENTAGE".equals(discountType)
                ? "Descuento " + formatPercent(discountValue) + "%"
                : "Descuento fijo";
    }

    /** Text of the confirmation shown when Enter applies a discount of {@code amount} pesos. */
    static String discountAppliedMessage(String discountType, double discountValue, double amount) {
        String shown = CurrencyFormatter.format(-amount);
        return "PERCENTAGE".equals(discountType)
                ? "Se aplicó un descuento de " + formatPercent(discountValue) + "% (" + shown + ")."
                : "Se aplicó un descuento fijo de " + shown + ".";
    }

    /** 20.0 -> "20", 12.5 -> "12,5". */
    private static String formatPercent(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /**
     * Cell factory that renders normal rows with the default text (String.valueOf)
     * and the display-only discount row with {@code discountRowText}.
     */
    private static <T> void installCartCellFactory(TableColumn<SaleItem, T> column,
                                                   java.util.function.Function<T, String> discountRowText) {
        column.setCellFactory(col -> new TableCell<SaleItem, T>() {
            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    return;
                }
                boolean discountRow = getIndex() >= 0 && getIndex() < getTableView().getItems().size()
                        && getTableView().getItems().get(getIndex()) instanceof DiscountCartLine;
                setText(discountRow ? discountRowText.apply(value) : String.valueOf(value));
            }
        });
    }

    /**
     * View-only row that shows the sale-level discount as a negative line in the cart.
     * It exists only in the table's item list: it is NEVER added to the presenter's
     * cart, never passed to the presenter as an item and never persisted. Removing it
     * from the UI clears the discount instead (see {@code onRemoveFromCart}).
     */
    static final class DiscountCartLine extends SaleItem {

        private final String label;

        /**
         * @param label  text for the product column
         * @param amount positive discount amount; shown as a negative subtotal
         */
        DiscountCartLine(String label, double amount) {
            this.label = label;
            setSubtotal(-amount);
        }

        String getLabel() {
            return label;
        }
    }
}
