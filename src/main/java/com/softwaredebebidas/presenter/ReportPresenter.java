package com.softwaredebebidas.presenter;

import com.softwaredebebidas.model.DailySalesDetailReport;
import com.softwaredebebidas.model.DailySalesDetailRow;
import com.softwaredebebidas.service.CsvService;
import com.softwaredebebidas.service.ReportService;
import com.softwaredebebidas.util.DateUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Presenter for the Reports module.
 * Handles report selection, date range management, and delegates to ReportService.
 */
public class ReportPresenter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReportService reportService;
    private final CsvService csvService;

    private String selectedReportType;
    private String fromDate;
    private String toDate;

    public ReportPresenter(ReportService reportService, CsvService csvService) {
        this.reportService = reportService;
        this.csvService = csvService;
    }

    // --- Report type selection ---

    public String getSelectedReportType() {
        return selectedReportType;
    }

    public void setSelectedReportType(String type) {
        this.selectedReportType = type;
    }

    // --- Date range ---

    public String getFromDate() {
        return fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public void setDateRange(String fromDate, String toDate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    /**
     * Sets date range from a preset: TODAY, THIS_WEEK, THIS_MONTH, LAST_30_DAYS.
     */
    public void setPresetDateRange(String preset) {
        LocalDate today = LocalDate.now();
        switch (preset) {
            case "TODAY" -> {
                fromDate = today.format(DATE_FORMATTER);
                toDate = today.format(DATE_FORMATTER);
            }
            case "THIS_WEEK" -> {
                LocalDate startOfWeek = today.with(java.time.DayOfWeek.MONDAY);
                fromDate = startOfWeek.format(DATE_FORMATTER);
                toDate = today.format(DATE_FORMATTER);
            }
            case "THIS_MONTH" -> {
                LocalDate startOfMonth = today.withDayOfMonth(1);
                fromDate = startOfMonth.format(DATE_FORMATTER);
                toDate = today.format(DATE_FORMATTER);
            }
            case "LAST_30_DAYS" -> {
                fromDate = today.minusDays(30).format(DATE_FORMATTER);
                toDate = today.format(DATE_FORMATTER);
            }
            default -> {
                fromDate = today.format(DATE_FORMATTER);
                toDate = today.format(DATE_FORMATTER);
            }
        }
    }

    // --- Report generation ---

    public List<ReportService.MarginReport> generateMarginReport() {
        return reportService.getMarginReport();
    }

    public List<ReportService.SalesPeriodReport> generateSalesByPeriodReport() {
        validateDateRange();
        return reportService.getSalesByPeriodReport(fromDateIso(), toDateIso());
    }

    public List<ReportService.ChannelReport> generateChannelComparisonReport() {
        validateDateRange();
        return reportService.getChannelComparisonReport(fromDateIso(), toDateIso());
    }

    public List<ReportService.RotationReport> generateRotationReport() {
        validateDateRange();
        return reportService.getRotationReport(fromDateIso(), toDateIso());
    }

    public List<ReportService.StockValueReport> generateStockValueReport() {
        return reportService.getStockValueReport();
    }

    /**
     * Returns the total stock value by summing the already-generated report rows,
     * without re-running the report query.
     */
    public double getStockValueTotal(List<ReportService.StockValueReport> reports) {
        return reports.stream()
                .mapToDouble(ReportService.StockValueReport::getTotalValue)
                .sum();
    }

    public List<ReportService.TopSellerReport> generateTopSellersReport() {
        return reportService.getTopSellersReport(10);
    }

    public DailySalesDetailReport generateDailySalesDetailReport() {
        validateDateRange();
        return reportService.getDailySalesDetailReport(fromDateIso(), toDateIso());
    }

    /**
     * Dispatches report generation based on selectedReportType.
     * Returns the result as a generic List (caller must cast based on type).
     */
    public Object generateReport() {
        if (selectedReportType == null || selectedReportType.isBlank()) {
            throw new IllegalArgumentException("Seleccione un tipo de reporte.");
        }

        return switch (selectedReportType) {
            case "MARGIN" -> generateMarginReport();
            case "SALES_PERIOD" -> {
                validateDateRange();
                yield generateSalesByPeriodReport();
            }
            case "CHANNEL" -> {
                validateDateRange();
                yield generateChannelComparisonReport();
            }
            case "ROTATION" -> {
                validateDateRange();
                yield generateRotationReport();
            }
            case "STOCK_VALUE" -> generateStockValueReport();
            case "TOP_SELLERS" -> generateTopSellersReport();
            case "DAILY_SALES_DETAIL" -> {
                validateDateRange();
                yield generateDailySalesDetailReport();
            }
            default -> throw new IllegalArgumentException("Tipo de reporte inválido: " + selectedReportType);
        };
    }

    private void validateDateRange() {
        if (fromDate == null || toDate == null || fromDate.isBlank() || toDate.isBlank()) {
            throw new IllegalArgumentException("Seleccione un rango de fechas válido.");
        }
        if (DateUtils.parse(fromDate).isAfter(DateUtils.parse(toDate))) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
    }

    /**
     * fromDate/toDate are kept in DD/MM/YYYY (the display format the DatePickers
     * use) — converted to ISO-8601 only at the boundary where they're passed to
     * ReportService/repositories, which query sale_date/purchase_date (ISO).
     */
    private String fromDateIso() {
        return DateUtils.toIso(DateUtils.parse(fromDate));
    }

    private String toDateIso() {
        return DateUtils.toIso(DateUtils.parse(toDate));
    }

    // ========================================
    // CSV Export
    // ========================================

    public Path exportMarginReportCsv() throws IOException {
        List<ReportService.MarginReport> data = generateMarginReport();
        List<String> headers = List.of("Producto", "Categoría", "Costo", "Venta", "Margen", "Margen %");
        return csvService.export("margen", headers, data, r -> List.of(
                r.getProductName(),
                r.getCategory(),
                String.format("%.2f", r.getCostPrice()),
                String.format("%.2f", r.getSalePrice()),
                String.format("%.2f", r.getMargin()),
                r.isZeroCost() ? "N/A" : String.format("%.1f%%", r.getMarginPercent())
        ));
    }

    public Path exportSalesPeriodCsv() throws IOException {
        validateDateRange();
        List<ReportService.SalesPeriodReport> data = generateSalesByPeriodReport();
        List<String> headers = List.of("Período", "Ingresos", "Transacciones", "Ticket Promedio");
        return csvService.export("ventas_periodo", headers, data, r -> List.of(
                r.getPeriod(),
                String.format("%.2f", r.getTotalRevenue()),
                String.valueOf(r.getTransactionCount()),
                String.format("%.2f", r.getAverageTicket())
        ));
    }

    public Path exportChannelCsv() throws IOException {
        validateDateRange();
        List<ReportService.ChannelReport> data = generateChannelComparisonReport();
        List<String> headers = List.of("Canal", "Ingresos", "Transacciones", "Participación %");
        return csvService.export("canales", headers, data, r -> List.of(
                r.getChannel(),
                String.format("%.2f", r.getRevenue()),
                String.valueOf(r.getTransactionCount()),
                String.format("%.1f%%", r.getPercentage())
        ));
    }

    public Path exportRotationCsv() throws IOException {
        validateDateRange();
        List<ReportService.RotationReport> data = generateRotationReport();
        List<String> headers = List.of("Producto", "Categoría", "Unidades Vendidas", "Stock Promedio", "Rotación");
        return csvService.export("rotacion", headers, data, r -> List.of(
                r.getProductName(),
                r.getCategory(),
                String.valueOf(r.getUnitsSold()),
                String.valueOf(r.getAverageStock()),
                String.format("%.2f", r.getRotation())
        ));
    }

    public Path exportStockValueCsv() throws IOException {
        List<ReportService.StockValueReport> data = generateStockValueReport();
        List<String> headers = List.of("Producto", "Categoría", "Stock Actual", "Costo Unitario", "Valor Total");
        return csvService.export("valor_stock", headers, data, r -> List.of(
                r.getProductName(),
                r.getCategory(),
                String.valueOf(r.getCurrentStock()),
                String.format("%.2f", r.getCostPrice()),
                String.format("%.2f", r.getTotalValue())
        ));
    }

    public Path exportTopSellersCsv() throws IOException {
        List<ReportService.TopSellerReport> data = generateTopSellersReport();
        List<String> headers = List.of("Producto", "Cantidad Vendida", "Total Ventas");
        return csvService.export("top_sellers", headers, data, r -> List.of(
                r.getProductName(),
                String.valueOf(r.getTotalQuantity()),
                String.format("%.2f", r.getTotalSales())
        ));
    }

    public Path exportDailySalesDetailCsv() throws IOException {
        validateDateRange();
        List<DailySalesDetailRow> data = generateDailySalesDetailReport().getRows();
        List<String> headers = List.of("Fecha", "Producto", "Cantidad", "Precio Unitario", "Total Línea");
        return csvService.export("ventas_detalladas", headers, data, r -> List.of(
                r.getDate(),
                r.getProductName(),
                String.valueOf(r.getQuantity()),
                String.format("%.2f", r.getUnitPrice()),
                String.format("%.2f", r.getLineTotal())
        ));
    }

    /**
     * Exports the currently selected report type to CSV.
     */
    public Path exportCurrentReportCsv() throws IOException {
        if (selectedReportType == null || selectedReportType.isBlank()) {
            throw new IllegalArgumentException("Seleccione un tipo de reporte.");
        }

        return switch (selectedReportType) {
            case "MARGIN" -> exportMarginReportCsv();
            case "SALES_PERIOD" -> exportSalesPeriodCsv();
            case "CHANNEL" -> exportChannelCsv();
            case "ROTATION" -> exportRotationCsv();
            case "STOCK_VALUE" -> exportStockValueCsv();
            case "TOP_SELLERS" -> exportTopSellersCsv();
            case "DAILY_SALES_DETAIL" -> exportDailySalesDetailCsv();
            default -> throw new IllegalArgumentException("Tipo de reporte inválido: " + selectedReportType);
        };
    }
}
