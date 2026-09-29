package com.softwaredebebidas.view;

import com.softwaredebebidas.model.Supplier;
import com.softwaredebebidas.presenter.SupplierPresenter;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.SupplierRepository;
import com.softwaredebebidas.util.AlertService;
import com.softwaredebebidas.util.Refreshable;
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
 * Controller for the Supplier management view.
 * Handles CRUD operations for suppliers.
 */
public class SupplierController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(SupplierController.class.getName());

    @FXML
    private TableView<Supplier> supplierTable;

    @FXML
    private TableColumn<Supplier, Long> colId;

    @FXML
    private TableColumn<Supplier, String> colName;

    @FXML
    private TableColumn<Supplier, String> colContact;

    @FXML
    private TableColumn<Supplier, String> colPhone;

    @FXML
    private TableColumn<Supplier, String> colEmail;

    @FXML
    private TableColumn<Supplier, String> colAddress;

    @FXML
    private Button btnNew;

    @FXML
    private Button btnEdit;

    @FXML
    private Button btnDelete;

    @FXML
    private GridPane formPane;

    @FXML
    private TextField nameField;

    @FXML
    private TextField contactField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField addressField;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnCancel;

    private SupplierPresenter presenter;
    private ObservableList<Supplier> supplierData;
    private Supplier editingSupplier;

    @FXML
    public void initialize() {
        DatabaseManager dbManager = com.softwaredebebidas.SoftwareDeBebidasApp.getDatabaseManager();
        presenter = new SupplierPresenter(new SupplierRepository(dbManager));

        setupTableColumns();
        loadSuppliers();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contact"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
    }

    private void loadSuppliers() {
        try {
            List<Supplier> suppliers = presenter.loadSuppliers();
            supplierData = FXCollections.observableArrayList(suppliers);
            supplierTable.setItems(supplierData);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudieron cargar los proveedores", e);
            AlertService.showErrorDialog("Error", "No se pudieron cargar los proveedores.");
        }
    }

    @FXML
    private void onNew() {
        editingSupplier = null;
        clearForm();
        formPane.setVisible(true);
        formPane.setManaged(true);
    }

    @FXML
    private void onEdit() {
        Supplier selected = supplierTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Proveedor", "Seleccione un proveedor para editar.");
            return;
        }
        editingSupplier = selected;
        populateForm(selected);
        formPane.setVisible(true);
        formPane.setManaged(true);
    }

    @FXML
    private void onDelete() {
        Supplier selected = supplierTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Proveedor", "Seleccione un proveedor para eliminar.");
            return;
        }
        boolean confirmed = AlertService.showConfirmDialog(
                "Confirmar Eliminación",
                "¿Está seguro que desea eliminar el proveedor: " + selected.getName() + "?"
        );
        if (confirmed) {
            try {
                presenter.deleteSupplier(selected.getId());
                loadSuppliers();
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "No se pudo eliminar el proveedor", e);
                AlertService.showErrorDialog("Error", "No se pudo eliminar el proveedor.");
            }
        }
    }

    @FXML
    private void onSave() {
        String name = nameField.getText().trim();
        if (!isValidSupplierName(name)) {
            AlertService.showErrorDialog("Error de Validación", "El nombre es obligatorio.");
            return;
        }

        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setContact(contactField.getText().trim());
        supplier.setPhone(phoneField.getText().trim());
        supplier.setEmail(emailField.getText().trim());
        supplier.setAddress(addressField.getText().trim());

        try {
            if (editingSupplier != null) {
                supplier.setId(editingSupplier.getId());
                presenter.updateSupplier(supplier);
            } else {
                presenter.saveSupplier(supplier);
            }
            formPane.setVisible(false);
            formPane.setManaged(false);
            loadSuppliers();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo guardar el proveedor", e);
            AlertService.showErrorDialog("Error", "No se pudo guardar el proveedor.");
        }
    }

    @FXML
    private void onCancel() {
        formPane.setVisible(false);
        formPane.setManaged(false);
        editingSupplier = null;
    }

    private void populateForm(Supplier supplier) {
        nameField.setText(supplier.getName());
        contactField.setText(supplier.getContact());
        phoneField.setText(supplier.getPhone());
        emailField.setText(supplier.getEmail());
        addressField.setText(supplier.getAddress());
    }

    private void clearForm() {
        nameField.clear();
        contactField.clear();
        phoneField.clear();
        emailField.clear();
        addressField.clear();
    }

    static boolean isValidSupplierName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    @Override
    public void refresh() {
        loadSuppliers();
    }
}
