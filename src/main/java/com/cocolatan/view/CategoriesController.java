package com.cocolatan.view;

import com.cocolatan.model.Category;
import com.cocolatan.model.Subcategory;
import com.cocolatan.presenter.CategoryPresenter;
import com.cocolatan.repository.CategoryRepository;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.SubcategoryRepository;
import com.cocolatan.util.AlertService;
import com.cocolatan.util.Refreshable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the category/subcategory management screen.
 * Handles CRUD, ordering, active toggles, and delete-with-confirm for both
 * tables. Business logic is delegated to {@link CategoryPresenter}; pure
 * decision helpers live as package-private statics so the JaCoCo gate stays
 * covered.
 */
public class CategoriesController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(CategoriesController.class.getName());

    private enum FormMode { CATEGORY, SUBCATEGORY, NONE }

    @FXML
    private TableView<Category> categoriesTable;

    @FXML
    private TableColumn<Category, Long> colCatId;

    @FXML
    private TableColumn<Category, String> colCatName;

    @FXML
    private TableColumn<Category, Integer> colCatOrder;

    @FXML
    private TableColumn<Category, Boolean> colCatActive;

    @FXML
    private TableView<Subcategory> subcategoriesTable;

    @FXML
    private TableColumn<Subcategory, Long> colSubId;

    @FXML
    private TableColumn<Subcategory, String> colSubName;

    @FXML
    private TableColumn<Subcategory, Integer> colSubOrder;

    @FXML
    private TableColumn<Subcategory, Boolean> colSubActive;

    @FXML
    private GridPane formPane;

    @FXML
    private TextField nameField;

    @FXML
    private TextField sortOrderField;

    @FXML
    private CheckBox activeCheck;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnCancel;

    private CategoryPresenter presenter;
    private ObservableList<Category> categoryData;
    private ObservableList<Subcategory> subcategoryData;
    private FormMode formMode = FormMode.NONE;
    private Category editingCategory;
    private Subcategory editingSubcategory;

    @FXML
    public void initialize() {
        DatabaseManager dbManager = com.cocolatan.CocolatanApp.getDatabaseManager();
        presenter = new CategoryPresenter(
                new CategoryRepository(dbManager),
                new SubcategoryRepository(dbManager)
        );

        setupCategoryColumns();
        setupSubcategoryColumns();
        loadCategories();
        setupSubcategorySelectionRefresh();
    }

    private void setupCategoryColumns() {
        colCatId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCatName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCatOrder.setCellValueFactory(new PropertyValueFactory<>("sortOrder"));
        colCatActive.setCellValueFactory(new PropertyValueFactory<>("active"));
    }

    private void setupSubcategoryColumns() {
        colSubId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colSubName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colSubOrder.setCellValueFactory(new PropertyValueFactory<>("sortOrder"));
        colSubActive.setCellValueFactory(new PropertyValueFactory<>("active"));
    }

    private void setupSubcategorySelectionRefresh() {
        categoriesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            loadSubcategories(newVal);
        });
    }

    private void loadCategories() {
        try {
            List<Category> categories = presenter.getCategories();
            categoryData = FXCollections.observableArrayList(categories);
            categoriesTable.setItems(categoryData);
            loadSubcategories(categoriesTable.getSelectionModel().getSelectedItem());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudieron cargar las categorías", e);
            AlertService.showErrorDialog("Error", "No se pudieron cargar las categorías.");
        }
    }

    private void loadSubcategories(Category category) {
        try {
            if (category == null) {
                subcategoriesTable.getItems().clear();
                return;
            }
            List<Subcategory> subcategories = presenter.getSubcategories(category.getId());
            subcategoryData = FXCollections.observableArrayList(subcategories);
            subcategoriesTable.setItems(subcategoryData);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudieron cargar las subcategorías", e);
            AlertService.showErrorDialog("Error", "No se pudieron cargar las subcategorías.");
        }
    }

    // ──────────────────────────────────────────────
    // Category actions
    // ──────────────────────────────────────────────

    @FXML
    private void onNewCategory() {
        formMode = FormMode.CATEGORY;
        editingCategory = null;
        clearForm();
        formPane.setVisible(true);
        formPane.setManaged(true);
        nameField.requestFocus();
    }

    @FXML
    private void onEditCategory() {
        Category selected = categoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Categoría", "Seleccione una categoría para editar.");
            return;
        }
        formMode = FormMode.CATEGORY;
        editingCategory = selected;
        populateForm(selected.getName(), selected.getSortOrder(), selected.isActive());
        formPane.setVisible(true);
        formPane.setManaged(true);
    }

    @FXML
    private void onDeleteCategory() {
        Category selected = categoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Categoría", "Seleccione una categoría para eliminar.");
            return;
        }
        int productCount;
        try {
            productCount = presenter.countProductsByCategory(selected.getId());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo verificar la categoría", e);
            AlertService.showErrorDialog("Error", "No se pudo verificar la categoría.");
            return;
        }
        boolean confirmed = AlertService.showConfirmDialog(
                "Confirmar Eliminación",
                buildCategoryDeleteWarning(selected.getName(), productCount)
        );
        if (!confirmed) {
            return;
        }
        try {
            presenter.deleteCategory(selected.getId());
            loadCategories();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo eliminar la categoría", e);
            AlertService.showErrorDialog("Error", "No se pudo eliminar la categoría.");
        }
    }

    @FXML
    private void onToggleCategoryActive() {
        Category selected = categoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Categoría", "Seleccione una categoría para cambiar su estado.");
            return;
        }
        try {
            presenter.toggleActive(selected);
            loadCategories();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo cambiar el estado de la categoría", e);
            AlertService.showErrorDialog("Error", "No se pudo cambiar el estado de la categoría.");
        }
    }

    @FXML
    private void onCategoryUp() {
        moveSelectedCategory(true);
    }

    @FXML
    private void onCategoryDown() {
        moveSelectedCategory(false);
    }

    private void moveSelectedCategory(boolean up) {
        int index = categoriesTable.getSelectionModel().getSelectedIndex();
        int target = moveTarget(index, up, categoryData.size());
        if (index == target || target < 0) {
            return;
        }
        try {
            swapSortOrder(categoryData.get(index), categoryData.get(target));
            presenter.updateCategory(categoryData.get(index));
            presenter.updateCategory(categoryData.get(target));
            loadCategories();
            categoriesTable.getSelectionModel().select(target);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo reordenar la categoría", e);
            AlertService.showErrorDialog("Error", "No se pudo reordenar la categoría.");
        }
    }

    // ──────────────────────────────────────────────
    // Subcategory actions
    // ──────────────────────────────────────────────

    @FXML
    private void onNewSubcategory() {
        Category category = categoriesTable.getSelectionModel().getSelectedItem();
        if (category == null) {
            AlertService.showWarningDialog("Seleccionar Categoría", "Seleccione una categoría para agregar subcategorías.");
            return;
        }
        formMode = FormMode.SUBCATEGORY;
        editingSubcategory = null;
        clearForm();
        formPane.setVisible(true);
        formPane.setManaged(true);
        nameField.requestFocus();
    }

    @FXML
    private void onEditSubcategory() {
        Subcategory selected = subcategoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Subcategoría", "Seleccione una subcategoría para editar.");
            return;
        }
        formMode = FormMode.SUBCATEGORY;
        editingSubcategory = selected;
        populateForm(selected.getName(), selected.getSortOrder(), selected.isActive());
        formPane.setVisible(true);
        formPane.setManaged(true);
    }

    @FXML
    private void onDeleteSubcategory() {
        Subcategory selected = subcategoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Subcategoría", "Seleccione una subcategoría para eliminar.");
            return;
        }
        boolean confirmed = AlertService.showConfirmDialog(
                "Confirmar Eliminación",
                buildSubcategoryDeleteWarning(selected.getName())
        );
        if (!confirmed) {
            return;
        }
        try {
            presenter.deleteSubcategory(selected.getId());
            loadSubcategories(categoriesTable.getSelectionModel().getSelectedItem());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo eliminar la subcategoría", e);
            AlertService.showErrorDialog("Error", "No se pudo eliminar la subcategoría.");
        }
    }

    @FXML
    private void onToggleSubcategoryActive() {
        Subcategory selected = subcategoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Subcategoría", "Seleccione una subcategoría para cambiar su estado.");
            return;
        }
        try {
            presenter.toggleSubcategoryActive(selected);
            loadSubcategories(categoriesTable.getSelectionModel().getSelectedItem());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo cambiar el estado de la subcategoría", e);
            AlertService.showErrorDialog("Error", "No se pudo cambiar el estado de la subcategoría.");
        }
    }

    @FXML
    private void onSubcategoryUp() {
        moveSelectedSubcategory(true);
    }

    @FXML
    private void onSubcategoryDown() {
        moveSelectedSubcategory(false);
    }

    private void moveSelectedSubcategory(boolean up) {
        int index = subcategoriesTable.getSelectionModel().getSelectedIndex();
        int target = moveTarget(index, up, subcategoryData.size());
        if (index == target || target < 0) {
            return;
        }
        try {
            swapSortOrder(subcategoryData.get(index), subcategoryData.get(target));
            presenter.updateSubcategory(subcategoryData.get(index));
            presenter.updateSubcategory(subcategoryData.get(target));
            loadSubcategories(categoriesTable.getSelectionModel().getSelectedItem());
            subcategoriesTable.getSelectionModel().select(target);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo reordenar la subcategoría", e);
            AlertService.showErrorDialog("Error", "No se pudo reordenar la subcategoría.");
        }
    }

    // ──────────────────────────────────────────────
    // Shared form
    // ──────────────────────────────────────────────

    @FXML
    private void onSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            AlertService.showErrorDialog("Error de Validación", "El nombre es obligatorio.");
            return;
        }
        int sortOrder = parseSortOrder(sortOrderField.getText(), 0);

        try {
            if (formMode == FormMode.CATEGORY) {
                saveCategoryForm(name, sortOrder);
            } else if (formMode == FormMode.SUBCATEGORY) {
                saveSubcategoryForm(name, sortOrder);
            } else {
                AlertService.showWarningDialog("Guardar", "Seleccione categoría o subcategoría antes de guardar.");
                return;
            }
            closeForm();
            reloadAfterSave();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo guardar", e);
            AlertService.showErrorDialog("Error", "No se pudo guardar: " + e.getMessage());
        }
    }

    private void saveCategoryForm(String name, int sortOrder) {
        if (editingCategory != null) {
            editingCategory.setName(name);
            editingCategory.setSortOrder(sortOrder);
            editingCategory.setActive(activeCheck.isSelected());
            presenter.updateCategory(editingCategory);
        } else {
            Category category = new Category();
            category.setName(name);
            category.setSortOrder(sortOrder);
            category.setActive(activeCheck.isSelected());
            presenter.saveCategory(category);
        }
    }

    private void saveSubcategoryForm(String name, int sortOrder) {
        Category category = categoriesTable.getSelectionModel().getSelectedItem();
        if (category == null) {
            AlertService.showWarningDialog("Guardar", "Seleccione una categoría para guardar la subcategoría.");
            return;
        }
        if (editingSubcategory != null) {
            editingSubcategory.setName(name);
            editingSubcategory.setSortOrder(sortOrder);
            editingSubcategory.setActive(activeCheck.isSelected());
            presenter.updateSubcategory(editingSubcategory);
        } else {
            Subcategory subcategory = new Subcategory();
            subcategory.setCategoryId(category.getId());
            subcategory.setName(name);
            subcategory.setSortOrder(sortOrder);
            subcategory.setActive(activeCheck.isSelected());
            presenter.saveSubcategory(subcategory);
        }
    }

    private void reloadAfterSave() {
        if (formMode == FormMode.SUBCATEGORY) {
            loadSubcategories(categoriesTable.getSelectionModel().getSelectedItem());
        } else {
            loadCategories();
        }
    }

    @FXML
    private void onCancel() {
        closeForm();
    }

    private void closeForm() {
        formPane.setVisible(false);
        formPane.setManaged(false);
        formMode = FormMode.NONE;
        editingCategory = null;
        editingSubcategory = null;
    }

    private void populateForm(String name, int sortOrder, boolean active) {
        nameField.setText(name);
        sortOrderField.setText(String.valueOf(sortOrder));
        activeCheck.setSelected(active);
    }

    private void clearForm() {
        nameField.clear();
        sortOrderField.setText("0");
        activeCheck.setSelected(true);
    }

    // ──────────────────────────────────────────────
    // Pure helpers (package-private for unit tests)
    // ──────────────────────────────────────────────

    static String buildCategoryDeleteWarning(String categoryName, int productCount) {
        if (productCount > 0) {
            return "La categoría '" + categoryName + "' tiene " + productCount
                    + " producto(s); al eliminarla quedarán sin categoría.";
        }
        return "¿Está seguro que desea eliminar la categoría '" + categoryName + "'?";
    }

    static String buildSubcategoryDeleteWarning(String subcategoryName) {
        return "¿Está seguro que desea eliminar la subcategoría '" + subcategoryName
                + "'? Sus productos quedarán solo con la categoría.";
    }

    static int parseSortOrder(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException e) {
            // Intentionally swallowed: a non-numeric sort-order field is user input
            // we can't parse, so the caller falls back to a safe default. Logged at
            // FINE so malformed input stays observable without noise.
            LOGGER.log(Level.FINE, "Sort order field is not a valid number; using fallback", e);
            return fallback;
        }
    }

    static int moveTarget(int index, boolean up, int size) {
        if (size < 2) return index;
        int target = up ? index - 1 : index + 1;
        if (target < 0 || target >= size) return index;
        return target;
    }

    private static void swapSortOrder(Category a, Category b) {
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
    }

    private static void swapSortOrder(Subcategory a, Subcategory b) {
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
    }

    @Override
    public void refresh() {
        loadCategories();
    }
}
