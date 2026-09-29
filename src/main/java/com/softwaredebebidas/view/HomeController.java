package com.softwaredebebidas.view;

import com.softwaredebebidas.SoftwareDeBebidasApp;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.presenter.AlertPresenter;
import com.softwaredebebidas.presenter.HomePresenter;
import com.softwaredebebidas.presenter.MainPresenter;
import com.softwaredebebidas.repository.AlertDismissalRepository;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.PurchaseRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.service.AlertService;
import com.softwaredebebidas.service.AuthService;
import com.softwaredebebidas.service.BackupService;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.util.CurrencyFormatter;
import com.softwaredebebidas.util.Refreshable;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HomeController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(HomeController.class.getName());

    @FXML
    private Label activeProductsLabel;

    @FXML
    private Label lowStockLabel;

    @FXML
    private Label todaySalesLabel;

    @FXML
    private Label alertsLabel;

    @FXML
    private Label outOfStockLabel;

    @FXML
    private Button backupButton;

    @FXML
    private TableView<Sale> recentSalesTable;

    @FXML
    private TableColumn<Sale, String> colDate;

    @FXML
    private TableColumn<Sale, Double> colTotal;

    @FXML
    private TableColumn<Sale, String> colChannel;

    @FXML
    private TableColumn<Sale, String> colPayment;

    private HomePresenter homePresenter;
    private AlertPresenter alertPresenter;

    @FXML
    public void initialize() {
        DatabaseManager dbManager = SoftwareDeBebidasApp.getDatabaseManager();
        if (dbManager == null) return;

        homePresenter = new HomePresenter(new SaleRepository(dbManager), new ProductRepository(dbManager));
        alertPresenter = new AlertPresenter(
                new AlertService(
                        new ProductRepository(dbManager),
                        new StockMovementRepository(dbManager),
                        new PurchaseRepository(dbManager),
                        new InventoryService(new StockMovementRepository(dbManager), new ProductRepository(dbManager)),
                        new AlertDismissalRepository(dbManager)
                )
        );
        setupTable();
        loadDashboardData();
        configureAdminButton();
    }

    private void configureAdminButton() {
        if (!AuthService.getInstance().isAdmin()) {
            backupButton.setVisible(false);
            backupButton.setManaged(false);
        }
    }

    private void setupTable() {
        colDate.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                formatSaleDate(data.getValue().getSaleDate())));
        colTotal.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalAmount"));
        colTotal.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : CurrencyFormatter.format(item));
            }
        });
        colChannel.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                formatChannel(data.getValue().getChannel())
        ));
        colPayment.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getPaymentMethod()));
    }

    static String formatChannel(String channel) {
        return "IN".equals(channel) ? "Local" : "PedidosYa";
    }

    static String formatSaleDate(String isoDate) {
        return com.softwaredebebidas.util.DateUtils.toDisplay(isoDate);
    }

    private void loadDashboardData() {
        try {
            double todayTotal = homePresenter.getTodaySales();
            todaySalesLabel.setText(CurrencyFormatter.format(todayTotal));

            int activeCount = homePresenter.getActiveProductsCount();
            activeProductsLabel.setText(String.valueOf(activeCount));

            int lowStockCount = homePresenter.getLowStockCount();
            lowStockLabel.setText(String.valueOf(lowStockCount));

            int outOfStockCount = homePresenter.getOutOfStockCount();
            outOfStockLabel.setText(String.valueOf(outOfStockCount));

            int alertCount = alertPresenter.getAlertCount();
            alertsLabel.setText(String.valueOf(alertCount));

            List<Sale> recent = homePresenter.getRecentSales();
            recentSalesTable.setItems(FXCollections.observableArrayList(recent));
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al cargar datos del dashboard", e);
            com.softwaredebebidas.util.AlertService.showErrorDialog("Error", "Error al cargar datos del dashboard.");
        }
    }

    @FXML
    private void onBackup() {
        try {
            BackupService service = SoftwareDeBebidasApp.getBackupService();
            if (service == null) {
                com.softwaredebebidas.util.AlertService.showErrorDialog("Error", "Servicio de backup no disponible.");
                return;
            }
            Path backupFile = service.createBackup();
            service.cleanOldBackups(10);
            com.softwaredebebidas.util.AlertService.showInfoDialog("Backup", "Backup creado exitosamente:\n" + backupFile.toAbsolutePath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al crear backup", e);
            com.softwaredebebidas.util.AlertService.showErrorDialog("Error", "Error al crear backup: " + e.getMessage());
        }
    }

    @FXML
    private void onQuickSale() {
        MainPresenter instance = MainPresenter.getInstance();
        if (instance != null) instance.onVentas();
    }

    @FXML
    private void onQuickProducts() {
        MainPresenter instance = MainPresenter.getInstance();
        if (instance != null) instance.onProductos();
    }

    @FXML
    private void onQuickPurchases() {
        MainPresenter instance = MainPresenter.getInstance();
        if (instance != null) instance.onCompras();
    }

    @FXML
    private void onQuickStock() {
        MainPresenter instance = MainPresenter.getInstance();
        if (instance != null) instance.onStock();
    }

    @Override
    public void refresh() {
        loadDashboardData();
    }
}
