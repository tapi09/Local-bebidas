package com.cocolatan.view;

import com.cocolatan.model.*;
import com.cocolatan.presenter.PurchasePresenter;
import com.cocolatan.repository.*;
import com.cocolatan.service.InventoryService;
import com.cocolatan.util.AlertService;
import com.cocolatan.util.CurrencyFormatter;
import com.cocolatan.util.DateUtils;
import com.cocolatan.util.PhotoUtils;
import com.cocolatan.util.Refreshable;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Purchase Entry view.
 * Delegates business logic to PurchasePresenter.
 */
public class PurchaseController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(PurchaseController.class.getName());

    @FXML
    private ComboBox<Supplier> supplierCombo;

    @FXML
    private TextField invoiceRefField;

    @FXML
    private DatePicker purchaseDatePicker;

    @FXML
    private TextArea notesField;

    @FXML
    private ComboBox<Product> productCombo;

    @FXML
    private TextField quantityField;

    @FXML
    private Button btnQtyMinus;

    @FXML
    private Button btnQtyPlus;

    @FXML
    private TextField unitCostField;

    @FXML
    private TextField lotNumberField;

    @FXML
    private TextField expiryField;

    @FXML
    private ComboBox<String> paymentMethodCombo;

    @FXML
    private Label stockInfoLabel;

    @FXML
    private TableView<PurchaseItem> itemsTable;

    @FXML
    private TableColumn<PurchaseItem, String> colItemProduct;

    @FXML
    private TableColumn<PurchaseItem, Integer> colItemQty;

    @FXML
    private TableColumn<PurchaseItem, Double> colItemCost;

    @FXML
    private TableColumn<PurchaseItem, Double> colItemSubtotal;

    @FXML
    private TableColumn<PurchaseItem, String> colItemLot;

    @FXML
    private TableColumn<PurchaseItem, String> colItemExpiry;

    @FXML
    private Label totalLabel;

    @FXML
    private Label subtotalLabel;

    @FXML
    private TextField taxField;

    @FXML
    private ImageView imgInvoicePreview;

    @FXML
    private Label lblPhotoName;

    @FXML
    private TableView<Purchase> historyTable;

    @FXML
    private TableColumn<Purchase, Long> colHistId;

    @FXML
    private TableColumn<Purchase, String> colHistSupplier;

    @FXML
    private TableColumn<Purchase, String> colHistDate;

    @FXML
    private TableColumn<Purchase, Double> colHistTotal;

    @FXML
    private TableColumn<Purchase, String> colHistRef;

    @FXML
    private TableColumn<Purchase, String> colHistPayment;

    private PurchasePresenter presenter;
    private CategoryRepository categoryRepository;
    private SubcategoryRepository subcategoryRepository;
    private final List<PurchaseItem> pendingItems = new ArrayList<>();
    private final Map<Long, String> productNameCache = new HashMap<>();
    private final Map<Long, String> supplierNameCache = new HashMap<>();
    private ObservableList<PurchaseItem> itemsData;
    private String invoicePhotoPath;

    // Cached lists for editable combos — same object references = no selection loss
    private List<Supplier> allCachedSuppliers;
    private List<Product> allCachedProducts;

    @FXML
    public void initialize() {
        DatabaseManager dbManager = com.cocolatan.CocolatanApp.getDatabaseManager();
        presenter = new PurchasePresenter(
                new PurchaseRepository(dbManager),
                new StockMovementRepository(dbManager),
                new ProductRepository(dbManager),
                new SupplierRepository(dbManager),
                dbManager
        );
        categoryRepository = new CategoryRepository(dbManager);
        subcategoryRepository = new SubcategoryRepository(dbManager);

        initializeInventoryService();
        setupTables();
        setupStringConverters();
        setupPaymentMethodCombo();
        loadData();
        purchaseDatePicker.setValue(LocalDate.now());

        // Set up search-as-you-type filtering (cached, no DB reload)
        setupProductComboSearch();
        setupSupplierComboSearch();

        // Update total when tax changes
        taxField.textProperty().addListener((obs, oldVal, newVal) -> updateTotal());
    }

    // ──────────────────────────────────────────────
    // Payment method combo
    // ──────────────────────────────────────────────

    private void setupPaymentMethodCombo() {
        paymentMethodCombo.setItems(FXCollections.observableArrayList(
                "Efectivo",
                "Tarjeta de Débito",
                "Tarjeta de Crédito",
                "Transferencia",
                "Cuenta Corriente",
                "Cheque",
                "Otro"
        ));
        paymentMethodCombo.setValue("Efectivo");
    }

    // ──────────────────────────────────────────────
    // StringConverters for editable combos
    // ──────────────────────────────────────────────

    private void setupStringConverters() {
        // Supplier converter: toString() shows name, fromString() matches by name from cache
        supplierCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Supplier s) {
                return s == null || s.getName() == null ? "" : s.getName();
            }

            @Override
            public Supplier fromString(String text) {
                if (text == null || text.isBlank() || allCachedSuppliers == null) return null;
                return allCachedSuppliers.stream()
                        .filter(s -> text.equals(s.getName()))
                        .findFirst()
                        .orElse(null);
            }
        });

        // Product converter: toString() shows "name — presentation", fromString() matches by same
        productCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product p) {
                return p == null ? "" : p.toString();
            }

            @Override
            public Product fromString(String text) {
                if (text == null || text.isBlank() || allCachedProducts == null) return null;
                return allCachedProducts.stream()
                        .filter(p -> text.equals(p.toString()))
                        .findFirst()
                        .orElse(null);
            }
        });
    }

    // ──────────────────────────────────────────────
    // Product combo search (cached, no DB reload)
    // ──────────────────────────────────────────────

    private void setupProductComboSearch() {
        productCombo.setEditable(true);
        // Filter when dropdown opens, not on every keystroke
        productCombo.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                applyProductFilter(productCombo.getEditor().getText());
            }
        });

        // When product is selected from combo, auto-fill cost
        productCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                unitCostField.setText(String.valueOf(newVal.getCostPrice()));
                updateStockInfo(newVal);
            } else {
                unitCostField.clear();
                stockInfoLabel.setText("");
            }
        });
    }

    private void applyProductFilter(String searchText) {
        if (allCachedProducts == null) return;
        ObservableList<Product> filtered = FXCollections.observableArrayList();
        for (Product p : allCachedProducts) {
            if (matchesProductFilter(searchText, p)) {
                filtered.add(p);
            }
        }
        productCombo.setItems(filtered);
    }

    static boolean matchesProductFilter(String searchText, Product p) {
        boolean emptyFilter = searchText == null || searchText.trim().isEmpty();
        if (emptyFilter) return true;
        String filter = searchText.toLowerCase().trim();
        return p.getName().toLowerCase().contains(filter)
            || (p.getPresentation() != null && p.getPresentation().toLowerCase().contains(filter))
            || (p.getBarcode() != null && p.getBarcode().toLowerCase().contains(filter))
            || (p.getCategory() != null && p.getCategory().toLowerCase().contains(filter));
    }

    // ──────────────────────────────────────────────
    // Supplier combo search (cached, no DB reload)
    // ──────────────────────────────────────────────

    private void setupSupplierComboSearch() {
        supplierCombo.setEditable(true);
        // Filter when dropdown opens, not on every keystroke
        supplierCombo.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                applySupplierFilter(supplierCombo.getEditor().getText());
            }
        });
    }

    private void applySupplierFilter(String searchText) {
        if (allCachedSuppliers == null) return;
        ObservableList<Supplier> filtered = FXCollections.observableArrayList();
        for (Supplier s : allCachedSuppliers) {
            if (matchesSupplierFilter(searchText, s)) {
                filtered.add(s);
            }
        }
        supplierCombo.setItems(filtered);
    }

    static boolean matchesSupplierFilter(String searchText, Supplier s) {
        boolean emptyFilter = searchText == null || searchText.trim().isEmpty();
        if (emptyFilter) return true;
        String filter = searchText.toLowerCase().trim();
        return s.getName().toLowerCase().contains(filter)
            || (s.getContact() != null && s.getContact().toLowerCase().contains(filter))
            || (s.getPhone() != null && s.getPhone().toLowerCase().contains(filter))
            || (s.getEmail() != null && s.getEmail().toLowerCase().contains(filter));
    }

    // ──────────────────────────────────────────────
    // Inventory service for stock info
    // ──────────────────────────────────────────────

    private InventoryService inventoryService;

    private void updateStockInfo(Product product) {
        try {
            int stock = inventoryService.getCurrentStock(product.getId());
            stockInfoLabel.setText("Stock actual: " + stock);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al cargar stock del producto", e);
            stockInfoLabel.setText("Error al cargar stock");
        }
    }

    private void initializeInventoryService() {
        DatabaseManager dbManager = com.cocolatan.CocolatanApp.getDatabaseManager();
        ProductRepository productRepo = new ProductRepository(dbManager);
        StockMovementRepository stockRepo = new StockMovementRepository(dbManager);
        inventoryService = new InventoryService(stockRepo, productRepo);
    }

    // ──────────────────────────────────────────────
    // Quick-create supplier
    // ──────────────────────────────────────────────

    @FXML
    private void onQuickSupplier() {
        Dialog<Supplier> dialog = new Dialog<>();
        dialog.setTitle("Nuevo Proveedor");
        dialog.setHeaderText("Ingrese los datos del nuevo proveedor");

        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Nombre");
        TextField contactField = new TextField();
        contactField.setPromptText("Contacto");
        TextField phoneField = new TextField();
        phoneField.setPromptText("Teléfono");
        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        TextField addressField = new TextField();
        addressField.setPromptText("Dirección");

        grid.add(new Label("Nombre:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Contacto:"), 0, 1);
        grid.add(contactField, 1, 1);
        grid.add(new Label("Teléfono:"), 0, 2);
        grid.add(phoneField, 1, 2);
        grid.add(new Label("Email:"), 0, 3);
        grid.add(emailField, 1, 3);
        grid.add(new Label("Dirección:"), 0, 4);
        grid.add(addressField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Supplier supplier = new Supplier();
                supplier.setName(nameField.getText().trim());
                supplier.setContact(contactField.getText().trim());
                supplier.setPhone(phoneField.getText().trim());
                supplier.setEmail(emailField.getText().trim());
                supplier.setAddress(addressField.getText().trim());
                return supplier;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newSupplier -> {
            try {
                Supplier savedSupplier = presenter.saveSupplier(newSupplier);
                if (savedSupplier != null && savedSupplier.getId() != null) {
                    reloadSuppliers();
                    // Find and select in the cached list
                    for (Supplier s : allCachedSuppliers) {
                        if (s.getId().equals(savedSupplier.getId())) {
                            supplierCombo.setValue(s);
                            break;
                        }
                    }
                    AlertService.showInfoDialog("Éxito", "Proveedor guardado correctamente.");
                } else {
                    AlertService.showErrorDialog("Error", "No se pudo guardar el proveedor.");
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error al guardar proveedor", e);
                AlertService.showErrorDialog("Error", "Error al guardar proveedor: " + e.getMessage());
            }
        });
    }

    // ──────────────────────────────────────────────
    // Quick-create product
    // ──────────────────────────────────────────────

    private List<Category> loadActiveCategories() {
        try {
            return categoryRepository.findAllActive();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al cargar categorías", e);
            return new ArrayList<>();
        }
    }

    private List<Subcategory> loadActiveSubcategories(long categoryId) {
        try {
            return subcategoryRepository.findAllActiveByCategoryId(categoryId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al cargar subcategorías", e);
            return new ArrayList<>();
        }
    }

    @FXML
    private void onQuickProduct() {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Nuevo Producto");
        dialog.setHeaderText("Ingrese los datos del nuevo producto");

        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Nombre (ej: Coca Cola)");
        TextField presentationField = new TextField();
        presentationField.setPromptText("Presentación (ej: 1.5L)");
        TextField costField = new TextField();
        costField.setPromptText("Costo");
        TextField priceField = new TextField();
        priceField.setPromptText("Precio venta");
        TextField pedidosyaField = new TextField();
        pedidosyaField.setPromptText("Venta (PedidosYa) — opcional");
        ComboBox<Category> categoryField = new ComboBox<>();
        categoryField.setPromptText("Categoría (opcional)");
        categoryField.setItems(FXCollections.observableArrayList(loadActiveCategories()));
        ComboBox<Subcategory> subcategoryField = new ComboBox<>();
        subcategoryField.setPromptText("Subcategoría (opcional)");
        subcategoryField.setDisable(true);

        // Cascade: picking a category loads its active subcategories and resets the previous one.
        categoryField.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                subcategoryField.setItems(FXCollections.observableArrayList());
                subcategoryField.setValue(null);
                subcategoryField.setDisable(true);
            } else {
                subcategoryField.setItems(FXCollections.observableArrayList(
                        loadActiveSubcategories(newVal.getId())));
                subcategoryField.setValue(null);
                subcategoryField.setDisable(false);
            }
        });
        TextField barcodeField = new TextField();
        barcodeField.setPromptText("Código de barras (opcional)");
        ComboBox<Supplier> supplierField = new ComboBox<>();
        supplierField.setItems(FXCollections.observableArrayList(allCachedSuppliers));
        supplierField.setConverter(new StringConverter<>() {
            @Override
            public String toString(Supplier s) {
                return s == null || s.getName() == null ? "" : s.getName();
            }

            @Override
            public Supplier fromString(String text) {
                if (text == null || text.isBlank() || allCachedSuppliers == null) return null;
                return allCachedSuppliers.stream()
                        .filter(s -> text.equals(s.getName()))
                        .findFirst()
                        .orElse(null);
            }
        });
        supplierField.setEditable(true);
        supplierField.setPromptText("Proveedor (opcional)");

        grid.add(new Label("Nombre:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Presentación:"), 0, 1);
        grid.add(presentationField, 1, 1);
        grid.add(new Label("Costo:"), 0, 2);
        grid.add(costField, 1, 2);
        grid.add(new Label("Precio venta:"), 0, 3);
        grid.add(priceField, 1, 3);
        grid.add(new Label("Venta (PedidosYa):"), 0, 4);
        grid.add(pedidosyaField, 1, 4);
        grid.add(new Label("Categoría:"), 0, 5);
        HBox categoryBox = new HBox(8);
        categoryBox.getChildren().addAll(categoryField, subcategoryField);
        grid.add(categoryBox, 1, 5);
        grid.add(new Label("Código:"), 0, 6);
        grid.add(barcodeField, 1, 6);
        grid.add(new Label("Proveedor:"), 0, 7);
        grid.add(supplierField, 1, 7);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    Product product = new Product();
                    product.setName(nameField.getText().trim());
                    product.setPresentation(presentationField.getText().trim());
                    product.setCostPrice(Double.parseDouble(costField.getText().trim()));
                    product.setSalePrice(Double.parseDouble(priceField.getText().trim()));
                    String pedidosyaText = pedidosyaField.getText().trim();
                    product.setPedidosyaPrice(pedidosyaText.isEmpty() ? 0 : Double.parseDouble(pedidosyaText));
                    Category selectedCategory = categoryField.getValue();
                    if (selectedCategory != null) {
                        product.setCategoryId(selectedCategory.getId());
                        product.setCategoryName(selectedCategory.getName());
                    }
                    Subcategory selectedSubcategory = subcategoryField.getValue();
                    if (selectedSubcategory != null) {
                        product.setSubcategoryId(selectedSubcategory.getId());
                        product.setSubcategoryName(selectedSubcategory.getName());
                    }
                    product.setBarcode(barcodeField.getText().trim().isEmpty() ? null : barcodeField.getText().trim());
                    Supplier selSup = supplierField.getValue();
                    if (selSup != null) {
                        product.setSupplierId(selSup.getId());
                    }
                    product.setActive(true);
                    product.setMinStock(0);
                    return product;
                } catch (NumberFormatException e) {
                    // Validation below
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newProduct -> {
            if (newProduct == null) {
                AlertService.showErrorDialog("Error", "Verifique los valores numéricos (costo, precio, venta PedidosYa).");
                return;
            }
            if (newProduct.getName() == null || newProduct.getName().isBlank()) {
                AlertService.showErrorDialog("Error", "El nombre del producto es obligatorio.");
                return;
            }
            try {
                Product savedProduct = presenter.saveProduct(newProduct);
                if (savedProduct != null && savedProduct.getId() != null) {
                    reloadProducts();
                    // Find and select in the cached list
                    for (Product p : allCachedProducts) {
                        if (p.getId().equals(savedProduct.getId())) {
                            productCombo.setValue(p);
                            break;
                        }
                    }
                    AlertService.showInfoDialog("Éxito", "Producto guardado correctamente.");
                } else {
                    AlertService.showErrorDialog("Error", "No se pudo guardar el producto.");
                }
            } catch (Exception e) {
                AlertService.showErrorDialog("Error", "Error al guardar producto: " + e.getMessage());
            }
        });
    }

    // ──────────────────────────────────────────────
    // Cache reload helpers
    // ──────────────────────────────────────────────

    private void reloadSuppliers() {
        allCachedSuppliers = presenter.loadSuppliers();
        supplierNameCache.clear();
        for (Supplier s : allCachedSuppliers) {
            supplierNameCache.put(s.getId(), s.getName());
        }
        // Restore all items using new filter method
        applySupplierFilter(supplierCombo.getEditor().getText());
    }

    private void reloadProducts() {
        allCachedProducts = presenter.loadProductsForDropdown();
        productNameCache.clear();
        for (Product p : allCachedProducts) {
            productNameCache.put(p.getId(), p.getName());
        }
        // Restore all items using new filter method
        applyProductFilter(productCombo.getEditor().getText());
    }

    // ──────────────────────────────────────────────
    // Refreshable
    // ──────────────────────────────────────────────

    @Override
    public void refresh() {
        reloadSuppliers();
        reloadProducts();
        loadHistory();
    }

    // ──────────────────────────────────────────────
    // Tax handler
    // ──────────────────────────────────────────────

    @FXML
    private void onTaxChanged() {
        updateTotal();
    }

    // ──────────────────────────────────────────────
    // Quantity +/- buttons
    // ──────────────────────────────────────────────

    @FXML
    private void onQtyMinus() {
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
    private void onQtyPlus() {
        try {
            int qty = Integer.parseInt(quantityField.getText().trim());
            quantityField.setText(String.valueOf(qty + 1));
        } catch (NumberFormatException e) {
            quantityField.setText("1");
        }
    }

    // ──────────────────────────────────────────────
    // Table setup
    // ──────────────────────────────────────────────

    private void setupTables() {
        // Items table
        colItemProduct.setCellValueFactory(cellData -> {
            PurchaseItem item = cellData.getValue();
            String name = resolveProductName(item.getProductId());
            return new SimpleStringProperty(name);
        });
        colItemQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colItemCost.setCellValueFactory(new PropertyValueFactory<>("unitCost"));
        colItemSubtotal.setCellValueFactory(cellData -> {
            PurchaseItem item = cellData.getValue();
            return new javafx.beans.property.SimpleDoubleProperty(item.getQuantity() * item.getUnitCost()).asObject();
        });
        colItemLot.setCellValueFactory(new PropertyValueFactory<>("lotNumber"));
        colItemExpiry.setCellValueFactory(new PropertyValueFactory<>("expiryDate"));

        // History table
        colHistId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colHistSupplier.setCellValueFactory(cellData -> {
            Purchase purchase = cellData.getValue();
            String name = supplierNameCache.getOrDefault(purchase.getSupplierId(),
                    "Proveedor #" + purchase.getSupplierId());
            return new SimpleStringProperty(name);
        });
        colHistDate.setCellValueFactory(new PropertyValueFactory<>("purchaseDate"));
        colHistTotal.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        colHistRef.setCellValueFactory(new PropertyValueFactory<>("invoiceRef"));
        colHistPayment.setCellValueFactory(cellData -> {
            String paymentMethod = cellData.getValue().getPaymentMethod();
            return new SimpleStringProperty(paymentMethod == null || paymentMethod.isBlank() ? "—" : paymentMethod);
        });
    }

    private String resolveProductName(Long productId) {
        return resolveProductName(productNameCache, productId);
    }

    static String resolveProductName(Map<Long, String> cache, Long productId) {
        return cache.getOrDefault(productId, "Producto #" + productId);
    }

    // ──────────────────────────────────────────────
    // Data loading
    // ──────────────────────────────────────────────

    private void loadData() {
        // Load and cache suppliers
        allCachedSuppliers = presenter.loadSuppliers();
        supplierNameCache.clear();
        for (Supplier s : allCachedSuppliers) {
            supplierNameCache.put(s.getId(), s.getName());
        }
        // Use fresh ObservableList for each combo - filters will replace items when dropdown opens
        supplierCombo.setItems(FXCollections.observableArrayList(allCachedSuppliers));

        // Load and cache products
        allCachedProducts = presenter.loadProductsForDropdown();
        productNameCache.clear();
        for (Product p : allCachedProducts) {
            productNameCache.put(p.getId(), p.getName());
        }
        // Use fresh ObservableList for each combo - filters will replace items when dropdown opens
        productCombo.setItems(FXCollections.observableArrayList(allCachedProducts));

        loadHistory();
    }

    private void loadHistory() {
        List<Purchase> history = presenter.loadPurchaseHistory();
        historyTable.setItems(FXCollections.observableArrayList(history));
    }

    // ──────────────────────────────────────────────
    // Item management
    // ──────────────────────────────────────────────

    @FXML
    private void onAddItem() {
        Product selectedProduct = productCombo.getValue();
        if (selectedProduct == null) {
            AlertService.showWarningDialog("Seleccionar Producto", "Seleccione un producto.");
            return;
        }

        try {
            Integer quantityValue = Integer.parseInt(quantityField.getText().trim());
            int quantity = quantityValue;
            double unitCost = Double.parseDouble(unitCostField.getText().trim());

            String validationError = validateItemFields(quantity, unitCost);
            if (validationError != null) {
                AlertService.showErrorDialog("Error", validationError);
                return;
            }

            PurchaseItem item = new PurchaseItem();
            item.setProductId(selectedProduct.getId());
            item.setQuantity(quantity);
            item.setUnitCost(unitCost);
            item.setLotNumber(lotNumberField.getText().trim().isEmpty() ? null : lotNumberField.getText().trim());
            item.setExpiryDate(expiryField.getText().trim().isEmpty() ? null : expiryField.getText().trim());

            pendingItems.add(item);
            itemsData = FXCollections.observableArrayList(pendingItems);
            itemsTable.setItems(itemsData);
            updateTotal();

            // Clear item fields
            quantityField.setText("1");
            unitCostField.clear();
            lotNumberField.clear();
            expiryField.clear();
            productCombo.getSelectionModel().clearSelection();
        } catch (NumberFormatException e) {
            AlertService.showErrorDialog("Error de Formato", "Ingrese valores numéricos válidos.");
        }
    }

    static String validateItemFields(int quantity, double unitCost) {
        if (quantity <= 0) {
            return "La cantidad debe ser mayor a 0.";
        }
        if (unitCost < 0) {
            return "El costo no puede ser negativo.";
        }
        return null;
    }

    @FXML
    private void onRemoveItem() {
        PurchaseItem selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Ítem", "Seleccione un ítem para quitar.");
            return;
        }
        pendingItems.remove(selected);
        itemsData = FXCollections.observableArrayList(pendingItems);
        itemsTable.setItems(itemsData);
        updateTotal();
    }

    @FXML
    private void onSavePurchase() {
        if (supplierCombo.getValue() == null) {
            AlertService.showErrorDialog("Error de Validación", "Seleccione un proveedor.");
            return;
        }
        if (purchaseDatePicker.getValue() == null) {
            AlertService.showErrorDialog("Error de Validación", "Seleccione una fecha.");
            return;
        }
        if (pendingItems.isEmpty()) {
            AlertService.showErrorDialog("Error de Validación", "Agregue al menos un ítem.");
            return;
        }

        Purchase purchase = new Purchase();
        purchase.setSupplierId(supplierCombo.getValue().getId());
        purchase.setInvoiceRef(invoiceRefField.getText().trim());
        purchase.setPurchaseDate(DateUtils.format(purchaseDatePicker.getValue()));
        purchase.setNotes(notesField.getText().trim());
        purchase.setInvoicePhotoPath(invoicePhotoPath);
        purchase.setPaymentMethod(paymentMethodCombo.getValue());

        try {
            double subtotal = presenter.calculateSubtotal(pendingItems);
            purchase.setSubtotal(subtotal);
            double tax = parseTax();
            purchase.setTaxAmount(tax);
            purchase.setTotalAmount(subtotal + tax);

            boolean saved = presenter.savePurchase(purchase, pendingItems);
            if (saved) {
                AlertService.showInfoDialog("Éxito", "Compra guardada correctamente.");
                pendingItems.clear();
                itemsData = FXCollections.observableArrayList();
                itemsTable.setItems(itemsData);
                updateTotal();
                loadHistory();
                clearForm();
            } else {
                AlertService.showErrorDialog("Error", "No se pudo guardar la compra.");
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al guardar compra", e);
            AlertService.showErrorDialog("Error", "Error al guardar compra.");
        }
    }

    @FXML
    private void onRefreshHistory() {
        loadHistory();
    }

    // ──────────────────────────────────────────────
    // Totals
    // ──────────────────────────────────────────────

    private void updateTotal() {
        double subtotal = pendingItems.stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitCost())
                .sum();
        double tax = parseTax();
        subtotalLabel.setText(CurrencyFormatter.format(subtotal));
        totalLabel.setText(CurrencyFormatter.format(subtotal + tax));
    }

    private double parseTax() {
        return parseTax(taxField.getText());
    }

    static double parseTax(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void clearForm() {
        invoiceRefField.clear();
        notesField.clear();
        purchaseDatePicker.setValue(LocalDate.now());
        supplierCombo.getSelectionModel().clearSelection();
        paymentMethodCombo.setValue("Efectivo");
        invoicePhotoPath = null;
        imgInvoicePreview.setImage(null);
        imgInvoicePreview.setVisible(false);
        lblPhotoName.setText("");
    }

    // ──────────────────────────────────────────────
    // Invoice photo attachment
    // ──────────────────────────────────────────────

    @FXML
    private void onAttachInvoice() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Factura");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.jpeg", "*.png"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile == null) return;

        if (selectedFile.length() > 10 * 1024 * 1024) {
            AlertService.showErrorDialog("Error", "El archivo es demasiado grande. El tamaño máximo es 10 MB.");
            return;
        }

        String photoPath = PhotoUtils.copyInvoiceImage(selectedFile);
        invoicePhotoPath = photoPath;
        imgInvoicePreview.setImage(new Image(selectedFile.toURI().toString()));
        imgInvoicePreview.setVisible(true);
        lblPhotoName.setText(selectedFile.getName());
    }

    public void onPurchaseLoaded(Purchase purchase) {
        if (purchase.getInvoicePhotoPath() != null && !purchase.getInvoicePhotoPath().isBlank()) {
            java.nio.file.Path absolutePath = PhotoUtils.resolvePath(purchase.getInvoicePhotoPath());
            if (absolutePath != null) {
                imgInvoicePreview.setImage(new Image(absolutePath.toUri().toString()));
                imgInvoicePreview.setVisible(true);
                lblPhotoName.setText(new File(purchase.getInvoicePhotoPath()).getName());
            }
        }
    }

    public void deleteInvoicePhoto() {
        if (imgInvoicePreview.getImage() != null) {
            imgInvoicePreview.setImage(null);
            imgInvoicePreview.setVisible(false);
            lblPhotoName.setText("");
        }
    }
}
