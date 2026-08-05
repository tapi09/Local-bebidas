package com.cocolatan.view;

import com.cocolatan.model.Product;
import com.cocolatan.model.StockMovement;
import com.cocolatan.presenter.StockPresenter;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.service.InventoryService;
import com.cocolatan.util.AlertService;
import com.cocolatan.util.Refreshable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Stock Management view.
 * Delegates business logic to StockPresenter.
 */
public class StockController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(StockController.class.getName());

    @FXML
    private Label countOk;

    @FXML
    private Label countLow;

    @FXML
    private Label countOut;

    @FXML
    private ComboBox<String> statusFilter;

    @FXML
    private TableView<StockPresenter.ProductStockInfo> dashboardTable;

    @FXML
    private TableColumn<StockPresenter.ProductStockInfo, String> colName;

    @FXML
    private TableColumn<StockPresenter.ProductStockInfo, String> colCategory;

    @FXML
    private TableColumn<StockPresenter.ProductStockInfo, Integer> colStock;

    @FXML
    private TableColumn<StockPresenter.ProductStockInfo, Integer> colMinStock;

    @FXML
    private TableColumn<StockPresenter.ProductStockInfo, String> colStatus;

    // Movement history filters
    @FXML
    private ComboBox<Product> productSelector;

    @FXML
    private ComboBox<String> movementTypeFilter;

    @FXML
    private TextField fromDateField;

    @FXML
    private TextField toDateField;

    @FXML
    private Label currentStockLabel;

    @FXML
    private Label selectedProductInfo;

    // Movements table
    @FXML
    private TableView<StockMovement> movementsTable;

    @FXML
    private TableColumn<StockMovement, String> colMovDate;

    @FXML
    private TableColumn<StockMovement, String> colMovProduct;

    @FXML
    private TableColumn<StockMovement, String> colMovType;

    @FXML
    private TableColumn<StockMovement, Integer> colMovQty;

    @FXML
    private TableColumn<StockMovement, String> colMovRef;

    @FXML
    private TableColumn<StockMovement, String> colMovNotes;

    // Adjustment section
    @FXML
    private VBox adjustmentSection;

    @FXML
    private ComboBox<Product> adjustProductSelector;

    @FXML
    private TextField adjustQuantityField;

    @FXML
    private TextArea adjustReasonField;

    private StockPresenter presenter;
    private List<Product> allProducts;

    // Last loaded dashboard data, reused for status filtering without re-querying.
    private List<StockPresenter.ProductStockInfo> dashboardData;

    @FXML
    public void initialize() {
        com.cocolatan.repository.DatabaseManager dbManager = com.cocolatan.CocolatanApp.getDatabaseManager();
        presenter = new StockPresenter(
                new InventoryService(new StockMovementRepository(dbManager), new ProductRepository(dbManager)),
                new ProductRepository(dbManager),
                new StockMovementRepository(dbManager)
        );

        setupTables();
        setupCombos();
        loadDashboard();
        loadProducts();

        // Hide adjustment section for non-admin users
        com.cocolatan.service.AuthService auth = com.cocolatan.service.AuthService.getInstance();
        if (auth.isCajero() && adjustmentSection != null) {
            adjustmentSection.setVisible(false);
            adjustmentSection.setManaged(false);
        }
    }

    private void setupTables() {
        // Dashboard table
        colName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getProductName()));
        colCategory.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getProductCategory()));
        colStock.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getCurrentStock()).asObject());
        colMinStock.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getMinStock()).asObject());
        colStatus.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getStatus()));

        // Movements table
        colMovDate.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCreatedAt()));
        colMovProduct.setCellValueFactory(data -> {
            Long pid = data.getValue().getProductId();
            return new javafx.beans.property.SimpleStringProperty(resolveMovementProductName(allProducts, pid));
        });
        colMovType.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getMovementType()));
        colMovQty.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getQuantity()).asObject());
        colMovRef.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                formatMovementReference(data.getValue().getReferenceType(), data.getValue().getReferenceId())));
        colMovNotes.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getNotes()));
    }

    private void setupCombos() {
        statusFilter.setItems(FXCollections.observableArrayList("OK", "BAJO", "SIN STOCK"));
        movementTypeFilter.setItems(FXCollections.observableArrayList("Todos", "ENTRY", "EXIT", "ADJUSTMENT"));
        movementTypeFilter.getSelectionModel().selectFirst();
    }

    private void loadProducts() {
        allProducts = presenter.getAllProducts();
        ObservableList<Product> products = FXCollections.observableArrayList(allProducts);

        productSelector.setItems(products);
        productSelector.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId() + " — " + item.getName());
            }
        });
        productSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId() + " — " + item.getName());
            }
        });

        adjustProductSelector.setItems(products);
        adjustProductSelector.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId() + " — " + item.getName());
            }
        });
        adjustProductSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId() + " — " + item.getName());
            }
        });

        productSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                try {
                    int stock = presenter.getStockByProduct(newVal.getId());
                    currentStockLabel.setText(String.valueOf(stock));
                    selectedProductInfo.setText(newVal.getName());
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error al cargar stock del producto seleccionado", e);
                    currentStockLabel.setText("—");
                    selectedProductInfo.setText("");
                    AlertService.showErrorDialog("Error", "Error al cargar el stock del producto.");
                }
            } else {
                currentStockLabel.setText("—");
                selectedProductInfo.setText("");
            }
        });
    }

    private void loadDashboard() {
        try {
            dashboardData = presenter.getDashboardData();
            dashboardTable.setItems(FXCollections.observableArrayList(dashboardData));

            StockPresenter.StockStatusCounts counts = presenter.getStatusCounts();
            countOk.setText("OK: " + counts.getOk());
            countLow.setText("BAJO: " + counts.getLow());
            countOut.setText("SIN STOCK: " + counts.getOut());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al cargar el dashboard de stock", e);
            AlertService.showErrorDialog("Error", "Error al cargar el dashboard de stock.");
        }
    }

    @FXML
    private void onFilter() {
        String status = statusFilter.getValue();
        if (status == null) {
            AlertService.showWarningDialog("Filtrar", "Seleccione un estado.");
            return;
        }

        try {
            // Map display status to internal status
            String internalStatus = mapStatusFilter(status);

            // Filter the already-loaded dashboard data — no re-query on every filter.
            List<StockPresenter.ProductStockInfo> filtered = new ArrayList<>();
            if (dashboardData != null) {
                for (StockPresenter.ProductStockInfo info : dashboardData) {
                    if (info.getStatus().equals(internalStatus)) {
                        filtered.add(info);
                    }
                }
            }
            dashboardTable.setItems(FXCollections.observableArrayList(filtered));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al filtrar el stock", e);
            AlertService.showErrorDialog("Error", "Error al filtrar el stock.");
        }
    }

    @FXML
    private void onShowAll() {
        try {
            loadDashboard();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al mostrar todos los productos", e);
            AlertService.showErrorDialog("Error", "Error al mostrar todos los productos.");
        }
    }

    @FXML
    private void onSearchMovements() {
        try {
            Product selectedProduct = productSelector.getValue();
            String typeFilter = movementTypeFilter.getValue();
            String fromDate = fromDateField.getText().trim();
            String toDate = toDateField.getText().trim();

            Long productId = selectedProduct != null ? selectedProduct.getId() : null;
            String type = "Todos".equals(typeFilter) ? null : typeFilter;

            if (fromDate.isEmpty()) fromDate = null;
            if (toDate.isEmpty()) toDate = null;

            List<StockMovement> movements = presenter.getMovementHistory(productId, type, fromDate, toDate);
            movementsTable.setItems(FXCollections.observableArrayList(movements));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al buscar movimientos de stock", e);
            AlertService.showErrorDialog("Error", "Error al buscar movimientos de stock.");
        }
    }

    @FXML
    private void onClearMovementFilters() {
        productSelector.getSelectionModel().clearSelection();
        movementTypeFilter.getSelectionModel().selectFirst();
        fromDateField.clear();
        toDateField.clear();
        currentStockLabel.setText("—");
        selectedProductInfo.setText("");
        movementsTable.setItems(FXCollections.observableArrayList());
    }

    @FXML
    private void onApplyAdjustment() {
        Product selectedProduct = adjustProductSelector.getValue();
        String quantityText = adjustQuantityField.getText().trim();
        String reason = adjustReasonField.getText().trim();

        if (selectedProduct == null) {
            AlertService.showErrorDialog("Error", "Seleccione un producto.");
            return;
        }
        if (quantityText.isEmpty()) {
            AlertService.showErrorDialog("Error", "Ingrese una cantidad.");
            return;
        }
        if (reason.isEmpty()) {
            AlertService.showErrorDialog("Error", "Ingrese el motivo del ajuste.");
            return;
        }

        String validationError = validateAdjustmentQuantity(quantityText);
        if (validationError != null) {
            AlertService.showErrorDialog("Error", validationError);
            return;
        }

        try {
            int quantity = Integer.parseInt(quantityText);

            if (!AlertService.showConfirmDialog("Ajuste de Stock", "¿Está seguro de ajustar el stock?")) {
                return;
            }
            if (!AlertService.showConfirmDialog(
                    "⚠️ OPERACIÓN DELICADA",
                    "Este ajuste modificará el inventario directamente.\n\n" +
                    "Esta operación debe realizarse con cuidado ya que afecta el control de stock del negocio.\n\n" +
                    "¿Confirma que desea continuar?"
            )) {
                return;
            }

            presenter.adjustStock(selectedProduct.getId(), quantity, reason);
            AlertService.showInfoDialog("Éxito", "Ajuste de stock registrado correctamente.");
            adjustQuantityField.clear();
            adjustReasonField.clear();
            adjustProductSelector.getSelectionModel().clearSelection();
            loadDashboard();
            loadProducts();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al ajustar stock", e);
            AlertService.showErrorDialog("Error", "Error al ajustar stock.");
        }
    }

    static String mapStatusFilter(String status) {
        return switch (status) {
            case "BAJO" -> "LOW";
            case "SIN STOCK" -> "OUT";
            default -> status;
        };
    }

    static String resolveMovementProductName(List<Product> products, Long productId) {
        if (products == null) return String.valueOf(productId);
        return products.stream()
                .filter(p -> p.getId().equals(productId))
                .map(Product::getName)
                .findFirst()
                .orElse(String.valueOf(productId));
    }

    static String formatMovementReference(String referenceType, Long referenceId) {
        return referenceType + (referenceId != null ? " #" + referenceId : "");
    }

    static String validateAdjustmentQuantity(String quantityText) {
        if (quantityText == null || quantityText.trim().isEmpty()) {
            return "Ingrese una cantidad.";
        }
        try {
            int quantity = Integer.parseInt(quantityText.trim());
            if (quantity == 0) {
                return "La cantidad no puede ser cero.";
            }
            return null;
        } catch (NumberFormatException e) {
            return "Ingrese un valor numérico válido para la cantidad.";
        }
    }

    @Override
    public void refresh() {
        loadDashboard();
        loadProducts();
    }
}
