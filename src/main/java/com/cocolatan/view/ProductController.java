package com.cocolatan.view;

import com.cocolatan.model.Category;
import com.cocolatan.model.PriceTarget;
import com.cocolatan.model.Product;
import com.cocolatan.model.Subcategory;
import com.cocolatan.model.Supplier;
import com.cocolatan.presenter.ProductPresenter;
import com.cocolatan.repository.CategoryRepository;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.repository.SubcategoryRepository;
import com.cocolatan.repository.SupplierRepository;
import com.cocolatan.service.InventoryService;
import com.cocolatan.util.AlertService;
import com.cocolatan.util.CurrencyFormatter;
import com.cocolatan.util.PhotoUtils;
import com.cocolatan.util.Refreshable;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Product Catalog view.
 * Delegates business logic to ProductPresenter.
 */
public class ProductController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(ProductController.class.getName());

    private static final String UNCATEGORIZED_SENTINEL = "(Sin categoría)";

    /**
     * Sentinel category filter id representing "Sin categoría". Real category
     * ids are positive AUTOINCREMENT values, so 0 never collides.
     */
    static final Long UNCATEGORIZED_ID = 0L;

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Product> productTable;

    @FXML
    private TableColumn<Product, Long> colId;

    @FXML
    private TableColumn<Product, String> colSku;

    @FXML
    private TableColumn<Product, String> colName;

    @FXML
    private TableColumn<Product, String> colCategory;

    @FXML
    private TableColumn<Product, String> colPresentation;

    @FXML
    private TableColumn<Product, Double> colCostPrice;

    @FXML
    private TableColumn<Product, Double> colSalePrice;

    @FXML
    private TableColumn<Product, Double> colPedidosyaPrice;

    @FXML
    private TableColumn<Product, String> colBarcode;

    @FXML
    private TableColumn<Product, Integer> colStock;

    @FXML
    private Button btnNew;

    @FXML
    private Button btnEdit;

    @FXML
    private Button btnDeactivate;

    @FXML
    private Button btnBulkPrice;

    @FXML
    private GridPane formPane;

    @FXML
    private TextField nameField;

    @FXML
    private ComboBox<Category> categoryCombo;

    @FXML
    private ComboBox<Subcategory> subcategoryCombo;

    @FXML
    private ComboBox<String> categoryFilter;

    @FXML
    private ComboBox<String> subcategoryFilter;

    @FXML
    private TextField presentationField;

    @FXML
    private TextField costPriceField;

    @FXML
    private TextField salePriceField;

    @FXML
    private TextField pedidosyaPriceField;

    @FXML
    private ComboBox<Supplier> supplierCombo;

    @FXML
    private TextField barcodeField;

    @FXML
    private TextField minStockField;

    @FXML
    private ImageView imagePreview;

    @FXML
    private Label photoNameLabel;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnCancel;

    private ProductPresenter presenter;
    private File selectedPhotoFile;
    private ObservableList<Product> productData;
    private Product editingProduct;
    private List<Product> currentBase;
    private Long categoryFilterId;
    private Long subcategoryFilterId;
    private Map<String, Long> categoryIdByName;
    private Map<String, Long> subcategoryIdByName;

    // Cached stock per product (batch query per load, no per-row DB hits).
    private Map<Long, Integer> stockMap = Collections.emptyMap();

    @FXML
    public void initialize() {
        DatabaseManager dbManager = com.cocolatan.CocolatanApp.getDatabaseManager();
        StockMovementRepository stockMovementRepository = new StockMovementRepository(dbManager);
        ProductRepository productRepository = new ProductRepository(dbManager);
        InventoryService inventoryService = new InventoryService(stockMovementRepository, productRepository);
        presenter = new ProductPresenter(productRepository, new SupplierRepository(dbManager), inventoryService,
                new CategoryRepository(dbManager), new SubcategoryRepository(dbManager));

        setupTableColumns();
        setupCascadeListeners();
        loadCategories();
        loadProducts();
        loadSuppliers();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colSku.setCellValueFactory(new PropertyValueFactory<>("sku"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("hierarchyLabel"));
        colPresentation.setCellValueFactory(new PropertyValueFactory<>("presentation"));
        colCostPrice.setCellValueFactory(new PropertyValueFactory<>("costPrice"));
        colSalePrice.setCellValueFactory(new PropertyValueFactory<>("salePrice"));
        colPedidosyaPrice.setCellValueFactory(new PropertyValueFactory<>("pedidosyaPrice"));
        colBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colStock.setCellValueFactory(cellData -> {
            Product product = cellData.getValue();
            int stock = stockMap.getOrDefault(product.getId(), 0);
            return new SimpleIntegerProperty(stock).asObject();
        });
    }

    /**
     * Cascades the form pickers: selecting a category loads its active
     * subcategories and resets the previous subcategory selection.
     */
    private void setupCascadeListeners() {
        categoryCombo.valueProperty().addListener((obs, oldVal, newVal) -> onCategorySelected(newVal));
    }

    private void onCategorySelected(Category category) {
        if (category == null) {
            subcategoryCombo.setItems(FXCollections.observableArrayList());
            subcategoryCombo.setValue(null);
            subcategoryCombo.setDisable(true);
        } else {
            List<Subcategory> subcategories = presenter.getSubcategoriesForCategory(category.getId());
            subcategoryCombo.setItems(FXCollections.observableArrayList(subcategories));
            subcategoryCombo.setValue(null);
            subcategoryCombo.setDisable(false);
        }
    }

    private void loadProducts() {
        currentBase = presenter.loadProducts();
        refreshStockMap();
        applyFilterAndSet();
    }

    /**
     * Recomputes the cached stock map for the currently loaded products in a
     * single batched query. Called whenever the underlying product list changes.
     */
    private void refreshStockMap() {
        if (currentBase == null) {
            stockMap = Collections.emptyMap();
            return;
        }
        List<Long> ids = currentBase.stream().map(Product::getId).toList();
        stockMap = presenter.getStockForProducts(ids);
    }

    private void loadSuppliers() {
        List<Supplier> suppliers = presenter.loadSuppliers();
        supplierCombo.setItems(FXCollections.observableArrayList(suppliers));
    }

    private void loadCategories() {
        String previousLabel = categoryFilter.getValue();
        List<Category> activeCategories = presenter.getCategories();
        categoryIdByName = new HashMap<>();
        List<String> filterItems = new ArrayList<>();
        filterItems.add("Todas");
        for (Category category : activeCategories) {
            filterItems.add(category.getName());
            categoryIdByName.put(category.getName(), category.getId());
        }
        filterItems.add(UNCATEGORIZED_SENTINEL);
        categoryFilter.setItems(FXCollections.observableArrayList(filterItems));
        categoryFilter.setValue(previousLabel != null && filterItems.contains(previousLabel)
                ? previousLabel
                : "Todas");

        categoryCombo.setItems(FXCollections.observableArrayList(activeCategories));
        subcategoryCombo.setItems(FXCollections.observableArrayList());
        subcategoryCombo.setValue(null);
        subcategoryCombo.setDisable(true);

        onCategoryFilter();
    }

    @FXML
    private void onCategoryFilter() {
        String label = categoryFilter.getValue();
        if (label == null || "Todas".equals(label)) {
            categoryFilterId = null;
        } else if (UNCATEGORIZED_SENTINEL.equals(label)) {
            categoryFilterId = UNCATEGORIZED_ID;
        } else {
            categoryFilterId = categoryIdByName.get(label);
        }
        subcategoryFilterId = null;
        loadSubcategoryFilterItems();
        applyFilterAndSet();
    }

    @FXML
    private void onSubcategoryFilter() {
        String label = subcategoryFilter.getValue();
        subcategoryFilterId = (label == null || "Todas".equals(label))
                ? null
                : subcategoryIdByName.get(label);
        applyFilterAndSet();
    }

    /**
     * Populates the subcategory filter with the selected category's active
     * subcategories. Enabled only when a concrete category is selected.
     */
    private void loadSubcategoryFilterItems() {
        subcategoryIdByName = new HashMap<>();
        List<String> items = new ArrayList<>();
        items.add("Todas");
        if (categoryFilterId != null && !UNCATEGORIZED_ID.equals(categoryFilterId)) {
            for (Subcategory subcategory : presenter.getSubcategoriesForCategory(categoryFilterId)) {
                items.add(subcategory.getName());
                subcategoryIdByName.put(subcategory.getName(), subcategory.getId());
            }
            subcategoryFilter.setDisable(false);
        } else {
            subcategoryFilter.setDisable(true);
        }
        subcategoryFilter.setItems(FXCollections.observableArrayList(items));
        subcategoryFilter.setValue("Todas");
    }

    private void applyFilterAndSet() {
        if (currentBase == null) {
            return;
        }
        List<Product> filtered = new ArrayList<>();
        String query = searchField.getText();
        for (Product product : currentBase) {
            if (matchesHierarchyFilter(product, categoryFilterId, subcategoryFilterId, query)) {
                filtered.add(product);
            }
        }
        productData = FXCollections.observableArrayList(filtered);
        productTable.setItems(productData);
    }

    /**
     * Returns true when the product passes the category and subcategory filters
     * AND the search query, using case-insensitive substring matching.
     * A null category filter means "Todas"; {@link #UNCATEGORIZED_ID} means
     * "Sin categoría" (product with no category). A null subcategory filter or
     * blank query does not restrict.
     */
    static boolean matchesHierarchyFilter(Product product, Long categoryFilterId,
                                          Long subcategoryFilterId, String query) {
        if (categoryFilterId != null) {
            if (UNCATEGORIZED_ID.equals(categoryFilterId)) {
                if (product.getCategoryId() != null) {
                    return false;
                }
            } else if (!categoryFilterId.equals(product.getCategoryId())) {
                return false;
            }
        }
        if (subcategoryFilterId != null && !subcategoryFilterId.equals(product.getSubcategoryId())) {
            return false;
        }
        if (query != null && !query.trim().isEmpty()) {
            String q = query.toLowerCase().trim();
            boolean nameMatch = product.getName() != null && product.getName().toLowerCase().contains(q);
            boolean barcodeMatch = product.getBarcode() != null && product.getBarcode().toLowerCase().contains(q);
            boolean skuMatch = product.getSku() != null && product.getSku().toLowerCase().contains(q);
            boolean categoryMatch = product.getCategoryName() != null
                    && product.getCategoryName().toLowerCase().contains(q);
            boolean subcategoryMatch = product.getSubcategoryName() != null
                    && product.getSubcategoryName().toLowerCase().contains(q);
            return nameMatch || barcodeMatch || skuMatch || categoryMatch || subcategoryMatch;
        }
        return true;
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            currentBase = presenter.loadProducts();
        } else {
            currentBase = presenter.searchByName(query);
        }
        refreshStockMap();
        applyFilterAndSet();
    }

    @FXML
    private void onClearSearch() {
        searchField.clear();
        loadProducts();
    }

    @FXML
    private void onNew() {
        editingProduct = null;
        clearForm();
        formPane.setVisible(true);
        formPane.setManaged(true);
    }

    @FXML
    private void onEdit() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Producto", "Seleccione un producto para editar.");
            return;
        }
        editingProduct = selected;
        populateForm(selected);
        formPane.setVisible(true);
        formPane.setManaged(true);
    }

    @FXML
    private void onDeactivate() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Producto", "Seleccione un producto para desactivar.");
            return;
        }
        boolean confirmed = AlertService.showConfirmDialog(
                "Confirmar Desactivación",
                "¿Está seguro que desea desactivar el producto: " + selected.getName() + "?"
        );
        if (confirmed) {
            try {
                boolean deactivated = presenter.safeDeactivateProduct(selected.getId());
                if (!deactivated) {
                    AlertService.showWarningDialog(
                            "No se puede desactivar",
                            "Este producto tiene historial de compras/ventas. Considere desactivarlo en su lugar."
                    );
                } else {
                    loadProducts();
                }
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Error al desactivar producto", e);
                AlertService.showErrorDialog("Error", "Error al desactivar producto.");
            }
        }
    }

    /**
     * Opens the bulk price update dialog: choose which price to adjust, the
     * product scope (all / category / subcategory / supplier) and a percentage,
     * with a live preview before applying.
     */
    @FXML
    private void onBulkPrice() {
        try {
            Dialog<Double> dialog = new Dialog<>();
            dialog.setTitle("Actualización masiva de precios");
            dialog.setHeaderText("Ajuste porcentual sobre los precios");

            ButtonType applyButtonType = new ButtonType("Aplicar", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

            ComboBox<String> priceTypeCombo = new ComboBox<>(
                    FXCollections.observableArrayList("Precio local", "Precio PedidosYa", "Ambos"));
            priceTypeCombo.setValue("Precio local");

            ComboBox<String> scopeCombo = new ComboBox<>(FXCollections.observableArrayList(
                    "Todos los productos", "Por categoría", "Por subcategoría", "Por proveedor"));
            scopeCombo.setValue("Todos los productos");

            Label selectorLabel = new Label("Categoría:");
            ComboBox<Object> selectorCombo = new ComboBox<>();
            selectorCombo.setPromptText("Seleccione...");
            selectorCombo.setDisable(true);

            TextField percentageField = new TextField();
            percentageField.setPromptText("5  (o -10 para descuento)");

            Label previewLabel = new Label();
            previewLabel.setWrapText(true);

            List<Category> categories = presenter.getCategories();
            List<Supplier> suppliers = presenter.loadSuppliers();

            scopeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                selectorCombo.setValue(null);
                selectorCombo.setItems(FXCollections.observableArrayList());
                selectorCombo.setDisable(true);
                if (newVal != null) {
                    switch (newVal) {
                        case "Por categoría":
                            selectorLabel.setText("Categoría:");
                            selectorCombo.setItems(FXCollections.observableArrayList(categories));
                            selectorCombo.setDisable(false);
                            break;
                        case "Por subcategoría":
                            selectorLabel.setText("Categoría:");
                            selectorCombo.setItems(FXCollections.observableArrayList(categories));
                            selectorCombo.setDisable(false);
                            break;
                        case "Por proveedor":
                            selectorLabel.setText("Proveedor:");
                            selectorCombo.setItems(FXCollections.observableArrayList(suppliers));
                            selectorCombo.setDisable(false);
                            break;
                        default:
                            break;
                    }
                }
                updateBulkPreview(priceTypeCombo, scopeCombo, selectorCombo, percentageField, previewLabel);
            });

            // Cascade: picking a category in "Por subcategoría" loads its subcategories.
            selectorCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if ("Por subcategoría".equals(scopeCombo.getValue()) && newVal instanceof Category) {
                    List<Subcategory> subs = presenter.getSubcategoriesForCategory(((Category) newVal).getId());
                    selectorLabel.setText("Subcategoría:");
                    selectorCombo.setItems(FXCollections.observableArrayList(subs));
                    selectorCombo.setValue(null);
                    selectorCombo.setPromptText(subs.isEmpty() ? "Sin subcategorías" : "Seleccione subcategoría...");
                }
                updateBulkPreview(priceTypeCombo, scopeCombo, selectorCombo, percentageField, previewLabel);
            });

            priceTypeCombo.valueProperty().addListener((obs, oldVal, newVal) ->
                    updateBulkPreview(priceTypeCombo, scopeCombo, selectorCombo, percentageField, previewLabel));
            percentageField.textProperty().addListener((obs, oldVal, newVal) ->
                    updateBulkPreview(priceTypeCombo, scopeCombo, selectorCombo, percentageField, previewLabel));

            grid.add(new Label("Precio a ajustar:"), 0, 0);
            grid.add(priceTypeCombo, 1, 0);
            grid.add(new Label("Alcance:"), 0, 1);
            grid.add(scopeCombo, 1, 1);
            grid.add(selectorLabel, 0, 2);
            grid.add(selectorCombo, 1, 2);
            grid.add(new Label("Porcentaje (+ aumento / - descuento):"), 0, 3);
            grid.add(percentageField, 1, 3);
            grid.add(previewLabel, 0, 4, 2, 1);

            dialog.getDialogPane().setContent(grid);

            // Tracks whether the user pressed Aplicar. Cancel and a parse failure
            // both convert to null, so the flag distinguishes them and keeps Cancel silent.
            boolean[] saveRequested = { false };

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == applyButtonType) {
                    saveRequested[0] = true;
                    try {
                        return parsePercentage(percentageField.getText());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                return null;
            });

            Optional<Double> dialogResult = dialog.showAndWait();
            if (dialogResult.isPresent()) {
                performBulkPriceUpdate(dialogResult.get(), priceTypeCombo, scopeCombo, selectorCombo);
            } else if (saveRequested[0]) {
                AlertService.showErrorDialog("Error", "Ingrese un porcentaje válido (ej: 5 o -10).");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error en actualización masiva", e);
            AlertService.showErrorDialog("Error", e.getMessage());
        }
    }

    /**
     * Validates the scope selection, applies the bulk update via the presenter,
     * refreshes the table and reports how many products were updated.
     */
    private void performBulkPriceUpdate(double percentage, ComboBox<String> priceTypeCombo,
                                        ComboBox<String> scopeCombo, ComboBox<Object> selectorCombo) {
        List<Product> products = resolveScopeProducts(scopeCombo, selectorCombo);
        if (products.isEmpty() && !"Todos los productos".equals(scopeCombo.getValue())) {
            AlertService.showWarningDialog("Selección",
                    "Seleccione una categoría, subcategoría o proveedor para aplicar el cambio.");
            return;
        }
        PriceTarget target;
        switch (priceTypeCombo.getValue()) {
            case "Precio PedidosYa":
                target = PriceTarget.PEDIDOSYA;
                break;
            case "Ambos":
                target = PriceTarget.BOTH;
                break;
            default:
                target = PriceTarget.LOCAL;
                break;
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        int updated = presenter.applyBulkPriceUpdate(ids, target, percentage);
        loadProducts();
        AlertService.showInfoDialog("Éxito", "Se actualizaron " + updated + " productos");
    }

    /**
     * Resolves the products in scope for the bulk update based on the selected
     * scope and selector value.
     */
    private List<Product> resolveScopeProducts(ComboBox<String> scopeCombo, ComboBox<Object> selectorCombo) {
        String scope = scopeCombo.getValue();
        if (scope == null || "Todos los productos".equals(scope)) {
            return presenter.loadProducts();
        }
        Object selection = selectorCombo.getValue();
        if (selection == null) {
            return Collections.emptyList();
        }
        switch (scope) {
            case "Por categoría":
                if (selection instanceof Category) {
                    return presenter.getProductsByCategory(((Category) selection).getId());
                }
                break;
            case "Por subcategoría":
                if (selection instanceof Subcategory) {
                    return presenter.getProductsBySubcategory(((Subcategory) selection).getId());
                }
                break;
            case "Por proveedor":
                if (selection instanceof Supplier) {
                    return presenter.getProductsBySupplier(((Supplier) selection).getId());
                }
                break;
            default:
                break;
        }
        return Collections.emptyList();
    }

    /**
     * Recomputes the live preview label: affected product count plus a sample
     * price before/after the percentage change. Empty when no valid input yet.
     */
    private void updateBulkPreview(ComboBox<String> priceTypeCombo, ComboBox<String> scopeCombo,
                                   ComboBox<Object> selectorCombo, TextField percentageField, Label previewLabel) {
        Double pct;
        try {
            pct = parsePercentage(percentageField.getText());
        } catch (NumberFormatException e) {
            previewLabel.setText("");
            return;
        }
        if (pct == 0) {
            previewLabel.setText("");
            return;
        }
        List<Product> products = resolveScopeProducts(scopeCombo, selectorCombo);
        if (products.isEmpty()) {
            previewLabel.setText("");
            return;
        }
        double multiplier = 1 + pct / 100.0;
        boolean pedidosyaTarget = "Precio PedidosYa".equals(priceTypeCombo.getValue());
        Product sample = products.get(0);
        double oldPrice = 0;
        for (Product product : products) {
            double price = pedidosyaTarget ? product.getPedidosyaPrice() : product.getSalePrice();
            if (price > 0) {
                sample = product;
                oldPrice = price;
                break;
            }
        }
        double newPrice = Math.round(oldPrice * multiplier * 100.0) / 100.0;
        previewLabel.setText(products.size() + " productos · ejemplo: " + sample.getName() + " "
                + CurrencyFormatter.format(oldPrice) + " → " + CurrencyFormatter.format(newPrice));
    }

    /**
     * Parses a percentage accepting an optional leading minus and decimals.
     *
     * @throws NumberFormatException when blank or not a number
     */
    private static Double parsePercentage(String text) throws NumberFormatException {
        if (text == null || text.trim().isEmpty()) {
            throw new NumberFormatException("Porcentaje vacío");
        }
        return Double.parseDouble(text.trim());
    }

    @FXML
    private void onSave() {
        Product product = buildProductFromForm();
        if (product == null) {
            return;
        }
        try {
            if (editingProduct != null) {
                product.setId(editingProduct.getId());
                if (!presenter.validateProduct(product)) {
                    AlertService.showErrorDialog("Error de Validación", "Verifique que todos los campos sean correctos.");
                    return;
                }
                presenter.updateProduct(product);
            } else {
                boolean saved = presenter.saveProduct(product);
                if (!saved) {
                    AlertService.showErrorDialog("Error de Validación", "Verifique que todos los campos sean correctos.");
                    return;
                }
            }
            formPane.setVisible(false);
            formPane.setManaged(false);
            loadCategories();
            loadProducts();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al guardar producto", e);
            AlertService.showErrorDialog("Error", "Error al guardar producto.");
        }
    }

    @FXML
    private void onCancel() {
        formPane.setVisible(false);
        formPane.setManaged(false);
        editingProduct = null;
        selectedPhotoFile = null;
    }

    @FXML
    private void onSelectImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar imagen del producto");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );
        File file = chooser.showOpenDialog(imagePreview.getScene().getWindow());
        if (file != null) {
            selectedPhotoFile = file;
            Image img = new Image(file.toURI().toString(), 120, 120, true, true);
            imagePreview.setImage(img);
            photoNameLabel.setText(file.getName());
        }
    }

    private Product buildProductFromForm() {
        Product product = new Product();
        try {
            product.setName(nameField.getText().trim());
            product.setPresentation(presentationField.getText().trim());
            product.setCostPrice(Double.parseDouble(costPriceField.getText().trim()));
            product.setSalePrice(Double.parseDouble(salePriceField.getText().trim()));
            String pedidosyaText = pedidosyaPriceField.getText().trim();
            product.setPedidosyaPrice(pedidosyaText.isEmpty() ? 0 : Double.parseDouble(pedidosyaText));
            product.setBarcode(barcodeField.getText().trim().isEmpty() ? null : barcodeField.getText().trim());
            String minStockText = minStockField.getText().trim();
            product.setMinStock(minStockText.isEmpty() ? 0 : Integer.parseInt(minStockText));
            product.setActive(true);

            // Hierarchy ids + names feed the legacy category write in the repository.
            Category selectedCategory = categoryCombo.getValue();
            if (selectedCategory != null) {
                product.setCategoryId(selectedCategory.getId());
                product.setCategoryName(selectedCategory.getName());
            }
            Subcategory selectedSubcategory = subcategoryCombo.getValue();
            if (selectedSubcategory != null) {
                product.setSubcategoryId(selectedSubcategory.getId());
                product.setSubcategoryName(selectedSubcategory.getName());
            }

            // If a new photo was selected, copy it and store the relative path
            if (selectedPhotoFile != null) {
                product.setPhotoPath(PhotoUtils.copyImage(selectedPhotoFile));
            } else if (editingProduct != null) {
                // Preserve existing photo when editing without changing it
                product.setPhotoPath(editingProduct.getPhotoPath());
            }

            Supplier selectedSupplier = supplierCombo.getValue();
            if (selectedSupplier != null) {
                product.setSupplierId(selectedSupplier.getId());
            }
        } catch (NumberFormatException e) {
            AlertService.showErrorDialog("Error de Formato", "Verifique los valores numéricos (costo, venta, stock).");
            return null;
        }
        return product;
    }

    private void populateForm(Product product) {
        nameField.setText(product.getName());
        Long categoryId = product.getCategoryId();
        if (categoryId != null) {
            Category category = findCategoryById(categoryId);
            if (category == null) {
                category = new Category();
                category.setId(categoryId);
                category.setName(product.getCategoryName());
                categoryCombo.getItems().add(category);
            }
            categoryCombo.setValue(category);
            Long subcategoryId = product.getSubcategoryId();
            if (subcategoryId != null) {
                Subcategory subcategory = findSubcategoryById(subcategoryId);
                if (subcategory == null) {
                    subcategory = new Subcategory();
                    subcategory.setId(subcategoryId);
                    subcategory.setName(product.getSubcategoryName());
                    subcategoryCombo.getItems().add(subcategory);
                }
                subcategoryCombo.setValue(subcategory);
            }
        } else {
            categoryCombo.setValue(null);
            subcategoryCombo.setValue(null);
            subcategoryCombo.setDisable(true);
        }
        presentationField.setText(product.getPresentation());
        costPriceField.setText(String.valueOf(product.getCostPrice()));
        salePriceField.setText(String.valueOf(product.getSalePrice()));
        pedidosyaPriceField.setText(product.getPedidosyaPrice() > 0 ? String.valueOf(product.getPedidosyaPrice()) : "");
        barcodeField.setText(product.getBarcode() != null ? product.getBarcode() : "");
        minStockField.setText(String.valueOf(product.getMinStock()));
        selectedPhotoFile = null;
        loadPhotoPreview(product.getPhotoPath());
    }

    private Category findCategoryById(Long id) {
        for (Category category : categoryCombo.getItems()) {
            if (category.getId().equals(id)) {
                return category;
            }
        }
        return null;
    }

    private Subcategory findSubcategoryById(Long id) {
        for (Subcategory subcategory : subcategoryCombo.getItems()) {
            if (subcategory.getId().equals(id)) {
                return subcategory;
            }
        }
        return null;
    }

    /**
     * Loads a product photo into the ImageView preview from its relative path.
     */
    private void loadPhotoPreview(String photoPath) {
        if (photoPath != null && !photoPath.isBlank()) {
            Path resolved = PhotoUtils.resolvePath(photoPath);
            if (resolved != null) {
                Image img = new Image(resolved.toUri().toString(), 120, 120, true, true);
                imagePreview.setImage(img);
                photoNameLabel.setText(extractFilename(photoPath));
                return;
            }
        }
        imagePreview.setImage(null);
        photoNameLabel.setText("");
    }

    private static String extractFilename(String path) {
        int idx = path.lastIndexOf('/');
        return (idx >= 0) ? path.substring(idx + 1) : path;
    }

    private void clearForm() {
        nameField.clear();
        categoryCombo.setValue(null);
        subcategoryCombo.setValue(null);
        subcategoryCombo.setDisable(true);
        presentationField.clear();
        costPriceField.clear();
        salePriceField.clear();
        pedidosyaPriceField.clear();
        barcodeField.clear();
        minStockField.clear();
        supplierCombo.getSelectionModel().clearSelection();
        selectedPhotoFile = null;
        imagePreview.setImage(null);
        photoNameLabel.setText("");
    }

    @Override
    public void refresh() {
        loadCategories();
        loadProducts();
    }
}
