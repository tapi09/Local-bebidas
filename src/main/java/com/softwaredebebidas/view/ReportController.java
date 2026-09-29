package com.softwaredebebidas.view;

import com.softwaredebebidas.presenter.ReportPresenter;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.service.CsvService;
import com.softwaredebebidas.service.ReportService;
import com.softwaredebebidas.util.AlertService;
import com.softwaredebebidas.util.Refreshable;
import com.softwaredebebidas.model.DailySalesDetailRow;
import com.softwaredebebidas.model.DailySalesDetailReport;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for the Reports view.
 * Delegates business logic to ReportPresenter.
 */
public class ReportController implements Refreshable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private ComboBox<String> reportTypeCombo;

    @FXML
    private GridPane dateRangePane;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private ComboBox<String> presetCombo;

    // Margin table
    @FXML
    private TableView<ReportService.MarginReport> marginTable;
    @FXML
    private TableColumn<ReportService.MarginReport, String> colMarginProduct;
    @FXML
    private TableColumn<ReportService.MarginReport, String> colMarginCategory;
    @FXML
    private TableColumn<ReportService.MarginReport, String> colMarginCost;
    @FXML
    private TableColumn<ReportService.MarginReport, String> colMarginSale;
    @FXML
    private TableColumn<ReportService.MarginReport, String> colMarginValue;
    @FXML
    private TableColumn<ReportService.MarginReport, String> colMarginPercent;

    // Sales period table
    @FXML
    private TableView<ReportService.SalesPeriodReport> salesPeriodTable;
    @FXML
    private TableColumn<ReportService.SalesPeriodReport, String> colSalesPeriod;
    @FXML
    private TableColumn<ReportService.SalesPeriodReport, String> colSalesRevenue;
    @FXML
    private TableColumn<ReportService.SalesPeriodReport, String> colSalesCount;
    @FXML
    private TableColumn<ReportService.SalesPeriodReport, String> colSalesTicket;

    // Channel comparison table
    @FXML
    private TableView<ReportService.ChannelReport> channelTable;
    @FXML
    private TableColumn<ReportService.ChannelReport, String> colChannelName;
    @FXML
    private TableColumn<ReportService.ChannelReport, String> colChannelRevenue;
    @FXML
    private TableColumn<ReportService.ChannelReport, String> colChannelCount;
    @FXML
    private TableColumn<ReportService.ChannelReport, String> colChannelPercent;

    // Rotation table
    @FXML
    private TableView<ReportService.RotationReport> rotationTable;
    @FXML
    private TableColumn<ReportService.RotationReport, String> colRotProduct;
    @FXML
    private TableColumn<ReportService.RotationReport, String> colRotCategory;
    @FXML
    private TableColumn<ReportService.RotationReport, String> colRotUnitsSold;
    @FXML
    private TableColumn<ReportService.RotationReport, String> colRotAvgStock;
    @FXML
    private TableColumn<ReportService.RotationReport, String> colRotValue;

    // Top sellers table
    @FXML
    private TableView<ReportService.TopSellerReport> topSellersTable;
    @FXML
    private TableColumn<ReportService.TopSellerReport, String> colTopProduct;
    @FXML
    private TableColumn<ReportService.TopSellerReport, String> colTopQty;
    @FXML
    private TableColumn<ReportService.TopSellerReport, String> colTopSales;

    // Stock value table
    @FXML
    private TableView<ReportService.StockValueReport> stockValueTable;
    @FXML
    private TableColumn<ReportService.StockValueReport, String> colSVProduct;
    @FXML
    private TableColumn<ReportService.StockValueReport, String> colSVCategory;
    @FXML
    private TableColumn<ReportService.StockValueReport, String> colSVStock;
    @FXML
    private TableColumn<ReportService.StockValueReport, String> colSVCost;
    @FXML
    private TableColumn<ReportService.StockValueReport, String> colSVTotal;

    // Daily sales detail table
    @FXML
    private TableView<DailySalesDetailRow> dailySalesDetailTable;
    @FXML
    private TableColumn<DailySalesDetailRow, String> colDailyDate;
    @FXML
    private TableColumn<DailySalesDetailRow, String> colDailyProduct;
    @FXML
    private TableColumn<DailySalesDetailRow, Integer> colDailyQty;
    @FXML
    private TableColumn<DailySalesDetailRow, String> colDailyUnitPrice;
    @FXML
    private TableColumn<DailySalesDetailRow, String> colDailyLineTotal;

    @FXML
    private Label stockValueTotalLabel;

    @FXML
    private javafx.scene.layout.HBox stockValueTotalPane;

    @FXML
    private Label dailySalesDetailTotalLabel;

    @FXML
    private javafx.scene.layout.HBox dailySalesDetailTotalPane;

    @FXML
    private Button exportButton;

    private ReportPresenter presenter;

    @FXML
    public void initialize() {
        com.softwaredebebidas.repository.DatabaseManager dbManager = com.softwaredebebidas.SoftwareDeBebidasApp.getDatabaseManager();
        CsvService csvService = new CsvService(com.softwaredebebidas.SoftwareDeBebidasApp.getExportDir());
        presenter = new ReportPresenter(
                new ReportService(
                        new ProductRepository(dbManager),
                        new SaleRepository(dbManager),
                        new StockMovementRepository(dbManager),
                        dbManager
                ),
                csvService
        );

        setupReportTypes();
        setupPresetCombo();
        setupAllTables();
        hideAllTables();
    }

    private void setupReportTypes() {
        reportTypeCombo.setItems(FXCollections.observableArrayList(
                "Margen por Producto",
                "Ventas por Período",
                "Comparación de Canales",
                "Rotación de Stock",
                "Valor de Stock",
                "Productos Más Vendidos",
                "Ventas Detalladas por Día"
        ));
        reportTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                presenter.setSelectedReportType(mapReportType(newVal));
                boolean needsDateRange = needsDateRange(newVal);
                dateRangePane.setVisible(needsDateRange);
                dateRangePane.setManaged(needsDateRange);
            }
        });
    }

    private void setupPresetCombo() {
        presetCombo.setItems(FXCollections.observableArrayList(
                "Hoy", "Esta Semana", "Este Mes", "Últimos 30 Días"
        ));
        presetCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                presenter.setPresetDateRange(mapPreset(newVal));
                fromDatePicker.setValue(LocalDate.parse(presenter.getFromDate(), DATE_FORMATTER));
                toDatePicker.setValue(LocalDate.parse(presenter.getToDate(), DATE_FORMATTER));
            }
        });
    }

    private void setupAllTables() {
        // Margin table
        colMarginProduct.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getProductName()));
        colMarginCategory.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCategory()));
        colMarginCost.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getCostPrice())));
        colMarginSale.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getSalePrice())));
        colMarginValue.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getMargin())));
        colMarginPercent.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                formatMarginPercent(data.getValue().isZeroCost(), data.getValue().getMarginPercent())));

        // Sales period table
        colSalesPeriod.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getPeriod()));
        colSalesRevenue.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getTotalRevenue())));
        colSalesCount.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().getTransactionCount())));
        colSalesTicket.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getAverageTicket())));

        // Channel table
        colChannelName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(formatChannel(data.getValue().getChannel())));
        colChannelRevenue.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getRevenue())));
        colChannelCount.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().getTransactionCount())));
        colChannelPercent.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("%.1f%%", data.getValue().getPercentage())));

        // Rotation table
        colRotProduct.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getProductName()));
        colRotCategory.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCategory()));
        colRotUnitsSold.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().getUnitsSold())));
        colRotAvgStock.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().getAverageStock())));
        colRotValue.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f", data.getValue().getRotation())));

        // Top sellers table
        colTopProduct.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getProductName()));
        colTopQty.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().getTotalQuantity())));
        colTopSales.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getTotalSales())));

        // Stock value table
        colSVProduct.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getProductName()));
        colSVCategory.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCategory()));
        colSVStock.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().getCurrentStock())));
        colSVCost.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getCostPrice())));
        colSVTotal.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getTotalValue())));

        // Daily sales detail table
        colDailyDate.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDate()));
        colDailyProduct.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getProductName()));
        colDailyQty.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getQuantity()).asObject());
        colDailyUnitPrice.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getUnitPrice())));
        colDailyLineTotal.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("$%,.2f", data.getValue().getLineTotal())));
    }

    private void hideAllTables() {
        marginTable.setVisible(false);
        marginTable.setManaged(false);
        salesPeriodTable.setVisible(false);
        salesPeriodTable.setManaged(false);
        channelTable.setVisible(false);
        channelTable.setManaged(false);
        rotationTable.setVisible(false);
        rotationTable.setManaged(false);
        topSellersTable.setVisible(false);
        topSellersTable.setManaged(false);
        stockValueTable.setVisible(false);
        stockValueTable.setManaged(false);
        stockValueTotalPane.setVisible(false);
        stockValueTotalPane.setManaged(false);
        dailySalesDetailTable.setVisible(false);
        dailySalesDetailTable.setManaged(false);
        dailySalesDetailTotalPane.setVisible(false);
        dailySalesDetailTotalPane.setManaged(false);
        dateRangePane.setVisible(false);
        dateRangePane.setManaged(false);
    }

    private void showOnlyTable(TableView<?> table) {
        hideAllTables();
        table.setVisible(true);
        table.setManaged(true);
    }

    @FXML
    private void onGenerateReport() {
        try {
            // Sync date pickers to presenter
            if (fromDatePicker.getValue() != null && toDatePicker.getValue() != null) {
                presenter.setDateRange(
                        fromDatePicker.getValue().format(DATE_FORMATTER),
                        toDatePicker.getValue().format(DATE_FORMATTER)
                );
            }

            String type = presenter.getSelectedReportType();
            if (type == null) {
                AlertService.showWarningDialog("Reportes", "Seleccione un tipo de reporte.");
                return;
            }

            switch (type) {
                case "MARGIN" -> {
                    showOnlyTable(marginTable);
                    List<ReportService.MarginReport> data = presenter.generateMarginReport();
                    marginTable.setItems(FXCollections.observableArrayList(data));
                }
                case "SALES_PERIOD" -> {
                    showOnlyTable(salesPeriodTable);
                    List<ReportService.SalesPeriodReport> data = presenter.generateSalesByPeriodReport();
                    salesPeriodTable.setItems(FXCollections.observableArrayList(data));
                }
                case "CHANNEL" -> {
                    showOnlyTable(channelTable);
                    List<ReportService.ChannelReport> data = presenter.generateChannelComparisonReport();
                    channelTable.setItems(FXCollections.observableArrayList(data));
                }
                case "ROTATION" -> {
                    showOnlyTable(rotationTable);
                    List<ReportService.RotationReport> data = presenter.generateRotationReport();
                    rotationTable.setItems(FXCollections.observableArrayList(data));
                }
                case "STOCK_VALUE" -> {
                    showOnlyTable(stockValueTable);
                    stockValueTotalPane.setVisible(true);
                    stockValueTotalPane.setManaged(true);
                    List<ReportService.StockValueReport> data = presenter.generateStockValueReport();
                    stockValueTable.setItems(FXCollections.observableArrayList(data));
                    stockValueTotalLabel.setText(String.format("$%,.2f", presenter.getStockValueTotal(data)));
                }
                case "TOP_SELLERS" -> {
                    showOnlyTable(topSellersTable);
                    List<ReportService.TopSellerReport> data = presenter.generateTopSellersReport();
                    topSellersTable.setItems(FXCollections.observableArrayList(data));
                }
                case "DAILY_SALES_DETAIL" -> {
                    showOnlyTable(dailySalesDetailTable);
                    DailySalesDetailReport report = presenter.generateDailySalesDetailReport();
                    dailySalesDetailTable.setItems(FXCollections.observableArrayList(report.getRows()));
                    dailySalesDetailTotalLabel.setText(String.format("$%,.2f", report.getGrandTotal()));
                    dailySalesDetailTotalPane.setVisible(true);
                    dailySalesDetailTotalPane.setManaged(true);
                }
            }
        } catch (IllegalArgumentException e) {
            AlertService.showWarningDialog("Reportes", e.getMessage());
        } catch (Exception e) {
            AlertService.showErrorDialog("Error", "Error al generar reporte: " + e.getMessage());
        }
    }

    static String mapReportType(String display) {
        return switch (display) {
            case "Margen por Producto" -> "MARGIN";
            case "Ventas por Período" -> "SALES_PERIOD";
            case "Comparación de Canales" -> "CHANNEL";
            case "Rotación de Stock" -> "ROTATION";
            case "Valor de Stock" -> "STOCK_VALUE";
            case "Productos Más Vendidos" -> "TOP_SELLERS";
            case "Ventas Detalladas por Día" -> "DAILY_SALES_DETAIL";
            default -> display;
        };
    }

    static String mapPreset(String display) {
        return switch (display) {
            case "Hoy" -> "TODAY";
            case "Esta Semana" -> "THIS_WEEK";
            case "Este Mes" -> "THIS_MONTH";
            case "Últimos 30 Días" -> "LAST_30_DAYS";
            default -> "TODAY";
        };
    }

    static boolean needsDateRange(String display) {
        return !"Margen por Producto".equals(display)
                && !"Valor de Stock".equals(display)
                && !"Productos Más Vendidos".equals(display);
    }

    static String formatMarginPercent(boolean isZeroCost, double marginPercent) {
        return isZeroCost ? "N/A" : String.format("%.1f%%", marginPercent);
    }

    static String formatChannel(String channel) {
        return switch (channel) {
            case "IN" -> "Local";
            case "PEDIDOSYA" -> "PedidosYa";
            default -> channel;
        };
    }

    @FXML
    private void onExportReport() {
        try {
            if (fromDatePicker.getValue() != null && toDatePicker.getValue() != null) {
                presenter.setDateRange(
                        fromDatePicker.getValue().format(DATE_FORMATTER),
                        toDatePicker.getValue().format(DATE_FORMATTER)
                );
            }

            Path file = presenter.exportCurrentReportCsv();
            AlertService.showInfoDialog("Exportar CSV", "Reporte exportado exitosamente:\n" + file.toAbsolutePath());
        } catch (IllegalArgumentException e) {
            AlertService.showWarningDialog("Exportar CSV", e.getMessage());
        } catch (Exception e) {
            AlertService.showErrorDialog("Error", "Error al exportar CSV: " + e.getMessage());
        }
    }

    private TableView<?> getActiveTable() {
        if (marginTable.isVisible()) return marginTable;
        if (salesPeriodTable.isVisible()) return salesPeriodTable;
        if (channelTable.isVisible()) return channelTable;
        if (rotationTable.isVisible()) return rotationTable;
        if (topSellersTable.isVisible()) return topSellersTable;
        if (stockValueTable.isVisible()) return stockValueTable;
        if (dailySalesDetailTable.isVisible()) return dailySalesDetailTable;
        return null;
    }

    // Reports are generated on demand by the user, so there is no data to
    // preload; the last generated report is kept in its cached table.
    @Override
    public void refresh() {
        // No-op: nothing to refresh for cached reports.
    }
}
