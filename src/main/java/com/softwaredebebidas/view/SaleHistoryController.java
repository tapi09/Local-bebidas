package com.softwaredebebidas.view;

import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.presenter.MainPresenter;
import com.softwaredebebidas.presenter.SaleHistoryPresenter;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.service.AuthService;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.service.ReceiptService;
import com.softwaredebebidas.service.SalesService;
import com.softwaredebebidas.util.AlertService;
import com.softwaredebebidas.util.CurrencyFormatter;
import com.softwaredebebidas.util.Refreshable;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.Optional;

/**
 * Controller for the Sale History view.
 * Displays active sales and allows cancellation.
 */
public class SaleHistoryController implements Refreshable {

    @FXML
    private TableView<Sale> salesTable;

    @FXML
    private TableColumn<Sale, String> colId;

    @FXML
    private TableColumn<Sale, String> colDate;

    @FXML
    private TableColumn<Sale, String> colCustomer;

    @FXML
    private TableColumn<Sale, String> colTotal;

    @FXML
    private TableColumn<Sale, String> colChannel;

    @FXML
    private TableColumn<Sale, String> colPayment;

    @FXML
    private TableColumn<Sale, String> colStatus;

    @FXML
    private TableColumn<Sale, String> colReceipt;

    @FXML
    private Button btnCancelSale;

    private SaleHistoryPresenter presenter;

    @FXML
    public void initialize() {
        com.softwaredebebidas.repository.DatabaseManager dbManager = com.softwaredebebidas.SoftwareDeBebidasApp.getDatabaseManager();
        SaleRepository saleRepo = new SaleRepository(dbManager);
        StockMovementRepository stockMovementRepo = new StockMovementRepository(dbManager);
        com.softwaredebebidas.repository.ProductRepository productRepo = new com.softwaredebebidas.repository.ProductRepository(dbManager);
        InventoryService inventoryService = new InventoryService(stockMovementRepo, productRepo);
        SalesService salesService = new SalesService(
                saleRepo, stockMovementRepo, productRepo, inventoryService,
                new ReceiptService(productRepo, new com.softwaredebebidas.repository.ConfigRepository(dbManager)), dbManager
        );
        presenter = new SaleHistoryPresenter(dbManager, saleRepo, salesService);

        colId.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.valueOf(cellData.getValue().getId())));
        colDate.setCellValueFactory(new PropertyValueFactory<>("saleDate"));
        colCustomer.setCellValueFactory(cellData -> {
            Long customerId = cellData.getValue().getCustomerId();
            return new SimpleStringProperty(formatCustomerLabel(customerId));
        });
        colTotal.setCellValueFactory(cellData ->
                new SimpleStringProperty(CurrencyFormatter.format(cellData.getValue().getTotalAmount())));
        colChannel.setCellValueFactory(cellData -> {
            String ch = cellData.getValue().getChannel();
            return new SimpleStringProperty(formatChannel(ch));
        });
        colPayment.setCellValueFactory(cellData -> {
            String pm = cellData.getValue().getPaymentMethod();
            return new SimpleStringProperty(formatPaymentMethod(pm));
        });
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colReceipt.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatReceipt(cellData.getValue().getReceiptText())));

        // Double-clicking a sale opens the same detail view as the "Ver Detalle" button.
        salesTable.setRowFactory(table -> {
            TableRow<Sale> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showSaleDetail(row.getItem());
                }
            });
            return row;
        });

        // Hide cancel button for non-admin users
        if (AuthService.getInstance().isCajero() && btnCancelSale != null) {
            btnCancelSale.setVisible(false);
            btnCancelSale.setManaged(false);
        }
    }

    @FXML
    private void onCancelSale() {
        Sale selected = salesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Venta", "Seleccione una venta para anular.");
            return;
        }

        if (!"ACTIVE".equals(selected.getStatus())) {
            AlertService.showWarningDialog("Venta Anulada", "Esta venta ya fue anulada.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Anular Venta");
        confirm.setHeaderText("¿Anular venta #" + selected.getId()
                + " por " + CurrencyFormatter.format(selected.getTotalAmount()) + "?");
        confirm.setContentText("Esta acción no se puede deshacer. El stock será restaurado automáticamente.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        TextInputDialog reasonDialog = new TextInputDialog();
        reasonDialog.setTitle("Motivo de Anulación");
        reasonDialog.setHeaderText("Ingrese el motivo de la anulación:");
        reasonDialog.setContentText("Motivo:");

        Optional<String> reason = reasonDialog.showAndWait();
        String cancellationReason = reason.orElse("");

        try {
            presenter.cancelSale(selected.getId(), cancellationReason);
            AlertService.showInfoDialog("Venta Anulada",
                    "Venta #" + selected.getId() + " anulada exitosamente. Stock restaurado.");
            refresh();
        } catch (IllegalStateException e) {
            // Idempotent second attempt (race / double-click): already cancelled
            AlertService.showWarningDialog("Venta Anulada",
                    "La venta #" + selected.getId() + " ya fue anulada.");
        } catch (RuntimeException e) {
            AlertService.showErrorDialog("Error", e.getMessage());
        }
    }

    @FXML
    private void onViewDetail() {
        Sale selected = salesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Venta", "Seleccione una venta para ver el detalle.");
            return;
        }
        showSaleDetail(selected);
    }

    /**
     * Shows the full receipt stored when the sale was made (products, quantities,
     * unit prices, discount, payment methods and total). Read-only: it only displays
     * sales.receipt_text, which reports never read.
     */
    private void showSaleDetail(Sale sale) {
        String receiptText = sale.getReceiptText();
        if (receiptText == null || receiptText.isBlank()) {
            AlertService.showInfoDialog(detailDialogTitle(sale), "Esta venta no tiene comprobante guardado.");
            return;
        }
        TextArea receiptArea = new TextArea(receiptText);
        receiptArea.setEditable(false);
        receiptArea.setWrapText(false);
        // Monospace keeps the receipt columns aligned as they were generated.
        receiptArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace;");
        receiptArea.setPrefColumnCount(48);
        receiptArea.setPrefRowCount(18);

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle(detailDialogTitle(sale));
        dialog.setHeaderText(detailDialogTitle(sale));
        dialog.getDialogPane().setContent(receiptArea);
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    /**
     * Title of the sale detail window; flags cancelled sales so an old receipt
     * is never mistaken for a valid one.
     */
    static String detailDialogTitle(Sale sale) {
        String title = "Detalle de Venta #" + sale.getId();
        return "CANCELLED".equals(sale.getStatus()) ? title + " — ANULADA" : title;
    }

    @FXML
    private void onBackToPos() {
        MainPresenter.getInstance().onVentas();
    }

    @Override
    public void refresh() {
        if (presenter == null) return;
        List<Sale> sales = presenter.loadActiveSales();
        ObservableList<Sale> data = FXCollections.observableArrayList(sales);
        salesTable.setItems(data);
    }

    static String formatCustomerLabel(Long customerId) {
        return customerId != null ? "Cliente #" + customerId : "Mostrador";
    }

    static String formatChannel(String channel) {
        return "IN".equals(channel) ? "Local" : "PedidosYa";
    }

    static String formatPaymentMethod(String paymentMethod) {
        return switch (paymentMethod) {
            case "CASH" -> "Efectivo";
            case "CREDIT_CARD" -> "Tarjeta Crédito";
            case "DEBIT_CARD" -> "Tarjeta Débito";
            case "TRANSFER" -> "Transferencia";
            case "MIXED" -> "Mixto";
            default -> paymentMethod;
        };
    }

    /**
     * Returns a compact single-line preview of a sale's receipt text for the
     * Comprobante column, or "—" when no receipt was generated.
     */
    static String formatReceipt(String receiptText) {
        if (receiptText == null || receiptText.isBlank()) {
            return "—";
        }
        String oneLine = receiptText.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "…";
    }
}
