package com.softwaredebebidas.view;

import com.softwaredebebidas.presenter.AlertPresenter;
import com.softwaredebebidas.repository.AlertDismissalRepository;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.PurchaseRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.service.AlertService;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.util.Refreshable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Alerts view.
 * Delegates business logic to AlertPresenter.
 */
public class AlertController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(AlertController.class.getName());

    @FXML
    private Label countExpiry;

    @FXML
    private Label countLowStock;

    @FXML
    private Label countTotal;

    @FXML
    private TableView<AlertService.Alert> expiryTable;

    @FXML
    private TableColumn<AlertService.Alert, String> colExpProduct;

    @FXML
    private TableColumn<AlertService.Alert, String> colExpLot;

    @FXML
    private TableColumn<AlertService.Alert, String> colExpDate;

    @FXML
    private TableColumn<AlertService.Alert, Long> colExpDays;

    @FXML
    private TableColumn<AlertService.Alert, String> colExpSeverity;

    @FXML
    private TableColumn<AlertService.Alert, Integer> colExpQty;

    @FXML
    private TableView<AlertService.Alert> lowStockTable;

    @FXML
    private TableColumn<AlertService.Alert, String> colLowProduct;

    @FXML
    private TableColumn<AlertService.Alert, Integer> colLowStock;

    @FXML
    private TableColumn<AlertService.Alert, Integer> colLowMin;

    @FXML
    private TableColumn<AlertService.Alert, String> colLowSeverity;

    private AlertPresenter presenter;

    @FXML
    public void initialize() {
        com.softwaredebebidas.repository.DatabaseManager dbManager = com.softwaredebebidas.SoftwareDeBebidasApp.getDatabaseManager();
        presenter = new AlertPresenter(
                new AlertService(
                        new ProductRepository(dbManager),
                        new StockMovementRepository(dbManager),
                        new PurchaseRepository(dbManager),
                        new InventoryService(new StockMovementRepository(dbManager), new ProductRepository(dbManager)),
                        new AlertDismissalRepository(dbManager)
                )
        );

        setupTables();
        loadAlerts();
    }

    private void setupTables() {
        // Expiry table
        colExpProduct.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getProductName()));
        colExpLot.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getLotNumber()));
        colExpDate.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getExpiryDate()));
        colExpDays.setCellValueFactory(data -> new javafx.beans.property.SimpleLongProperty(data.getValue().getDaysUntilExpiry()).asObject());
        colExpSeverity.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSeverity()));
        colExpQty.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getAffectedQuantity()).asObject());

        // Low stock table
        colLowProduct.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getProductName()));
        colLowStock.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getAffectedQuantity()).asObject());
        colLowMin.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getProduct().getMinStock()).asObject());
        colLowSeverity.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSeverity()));
    }

    private void loadAlerts() {
        try {
            List<AlertService.Alert> expiryAlerts = presenter.getExpiryAlerts();
            expiryTable.setItems(FXCollections.observableArrayList(expiryAlerts));

            List<AlertService.Alert> lowStockAlerts = presenter.getLowStockAlerts();
            lowStockTable.setItems(FXCollections.observableArrayList(lowStockAlerts));

            countExpiry.setText(formatCountLabel("Vencimiento", expiryAlerts.size()));
            countLowStock.setText(formatCountLabel("Stock Bajo", lowStockAlerts.size()));
            countTotal.setText(formatCountLabel("Total", totalAlertCount(expiryAlerts.size(), lowStockAlerts.size())));
        } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Error al cargar alertas", e);
                com.softwaredebebidas.util.AlertService.showErrorDialog("Error", "Error al cargar alertas.");
        }
    }

    @FXML
    private void onDismissExpiry() {
        AlertService.Alert selected = expiryTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                presenter.dismissAlert(selected);
                loadAlerts();
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Error al descartar alerta de vencimiento", e);
                com.softwaredebebidas.util.AlertService.showErrorDialog("Error", "Error al descartar alerta.");
            }
        }
    }

    @FXML
    private void onDismissLowStock() {
        AlertService.Alert selected = lowStockTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                presenter.dismissAlert(selected);
                loadAlerts();
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Error al descartar alerta de stock bajo", e);
                com.softwaredebebidas.util.AlertService.showErrorDialog("Error", "Error al descartar alerta.");
            }
        }
    }

    @FXML
    private void onRefresh() {
        loadAlerts();
    }

    static String formatCountLabel(String prefix, int count) {
        return prefix + ": " + count;
    }

    static int totalAlertCount(int expiryCount, int lowStockCount) {
        return expiryCount + lowStockCount;
    }

    @Override
    public void refresh() {
        loadAlerts();
    }
}
