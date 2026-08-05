package com.cocolatan.view;

import com.cocolatan.model.Category;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            int stock = presenter.getCurrentStock(product.getId());
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
        applyFilterAndSet();
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
