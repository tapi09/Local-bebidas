package com.cocolatan.presenter;

import com.cocolatan.service.CsvService;
import com.cocolatan.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportPresenterTest {

    @Mock
    private ReportService reportService;

    @Mock
    private CsvService csvService;

    private ReportPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new ReportPresenter(reportService, csvService);
    }

    // --- Report type selection ---

    @Test
    void setSelectedReportTypeStoresType() {
        presenter.setSelectedReportType("MARGIN");
        assertThat(presenter.getSelectedReportType()).isEqualTo("MARGIN");
    }

    @Test
    void setSelectedReportTypeDefaultsToNull() {
        assertThat(presenter.getSelectedReportType()).isNull();
    }

    // --- Date range ---

    @Test
    void setDateRangeStoresRange() {
        presenter.setDateRange("01/07/2026", "31/07/2026");
        assertThat(presenter.getFromDate()).isEqualTo("01/07/2026");
        assertThat(presenter.getToDate()).isEqualTo("31/07/2026");
    }

    @Test
    void setPresetDateRangeTodaySetsCorrectDates() {
        presenter.setPresetDateRange("TODAY");
        assertThat(presenter.getFromDate()).isEqualTo(presenter.getToDate());
    }

    @Test
    void setPresetDateRangeThisMonthSetsFirstAndLastDay() {
        presenter.setPresetDateRange("THIS_MONTH");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        assertThat(presenter.getFromDate()).isEqualTo(YearMonth.now().atDay(1).format(fmt));
        assertThat(presenter.getToDate()).isEqualTo(LocalDate.now().format(fmt));
    }

    @Test
    void setPresetDateRangeLast30DaysSetsCorrectRange() {
        presenter.setPresetDateRange("LAST_30_DAYS");
        assertThat(presenter.getFromDate()).isNotNull();
        assertThat(presenter.getToDate()).isNotNull();
        assertThat(presenter.getFromDate()).isNotEqualTo(presenter.getToDate());
    }

    // --- Generate margin report ---

    @Test
    void generateMarginReportReturnsData() {
        ReportService.MarginReport item = new ReportService.MarginReport(
                "Coca-Cola", "Gaseosas", 300.0, 600.0, 300.0, 100.0, false);
        when(reportService.getMarginReport()).thenReturn(Collections.singletonList(item));

        List<ReportService.MarginReport> result = presenter.generateMarginReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductName()).isEqualTo("Coca-Cola");
        verify(reportService).getMarginReport();
    }

    @Test
    void generateMarginReportDelegatesToService() {
        when(reportService.getMarginReport()).thenReturn(Collections.emptyList());

        presenter.generateMarginReport();

        verify(reportService).getMarginReport();
    }

    // --- Generate sales by period report ---

    @Test
    void generateSalesByPeriodReportUsesDateRange() {
        presenter.setDateRange("01/07/2026", "31/07/2026");
        when(reportService.getSalesByPeriodReport("01/07/2026", "31/07/2026"))
                .thenReturn(Collections.emptyList());

        presenter.generateSalesByPeriodReport();

        verify(reportService).getSalesByPeriodReport("01/07/2026", "31/07/2026");
    }

    @Test
    void generateSalesByPeriodReportReturnsData() {
        presenter.setDateRange("01/07/2026", "31/07/2026");
        ReportService.SalesPeriodReport item = new ReportService.SalesPeriodReport(
                "01/07/2026", 10000.0, 3, 3333.33);
        when(reportService.getSalesByPeriodReport("01/07/2026", "31/07/2026"))
                .thenReturn(Collections.singletonList(item));

        List<ReportService.SalesPeriodReport> result = presenter.generateSalesByPeriodReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalRevenue()).isCloseTo(10000.0, org.assertj.core.data.Offset.offset(0.01));
    }

    // --- Generate channel comparison report ---

    @Test
    void generateChannelComparisonReportUsesDateRange() {
        presenter.setDateRange("01/07/2026", "31/07/2026");
        when(reportService.getChannelComparisonReport("01/07/2026", "31/07/2026"))
                .thenReturn(Collections.emptyList());

        presenter.generateChannelComparisonReport();

        verify(reportService).getChannelComparisonReport("01/07/2026", "31/07/2026");
    }

    @Test
    void generateChannelComparisonReportReturnsData() {
        presenter.setDateRange("01/07/2026", "31/07/2026");
        ReportService.ChannelReport item = new ReportService.ChannelReport("IN", 20000.0, 10, 66.67);
        when(reportService.getChannelComparisonReport("01/07/2026", "31/07/2026"))
                .thenReturn(Collections.singletonList(item));

        List<ReportService.ChannelReport> result = presenter.generateChannelComparisonReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChannel()).isEqualTo("IN");
    }

    // --- Generate rotation report ---

    @Test
    void generateRotationReportUsesDateRange() {
        presenter.setDateRange("01/07/2026", "31/07/2026");
        when(reportService.getRotationReport("01/07/2026", "31/07/2026"))
                .thenReturn(Collections.emptyList());

        presenter.generateRotationReport();

        verify(reportService).getRotationReport("01/07/2026", "31/07/2026");
    }

    @Test
    void generateRotationReportReturnsData() {
        presenter.setDateRange("01/07/2026", "31/07/2026");
        ReportService.RotationReport item = new ReportService.RotationReport(
                "Coca-Cola", "Gaseosas", 120, 30, 4.0);
        when(reportService.getRotationReport("01/07/2026", "31/07/2026"))
                .thenReturn(Collections.singletonList(item));

        List<ReportService.RotationReport> result = presenter.generateRotationReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRotation()).isCloseTo(4.0, org.assertj.core.data.Offset.offset(0.01));
    }

    // --- Generate stock value report ---

    @Test
    void generateStockValueReportReturnsData() {
        ReportService.StockValueReport item = new ReportService.StockValueReport(
                "Coca-Cola", "Gaseosas", 50, 300.0, 15000.0);
        when(reportService.getStockValueReport()).thenReturn(Collections.singletonList(item));

        List<ReportService.StockValueReport> result = presenter.generateStockValueReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalValue()).isCloseTo(15000.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void getStockValueTotalReturnsSum() {
        when(reportService.getStockValueReportTotal()).thenReturn(25000.0);

        double total = presenter.getStockValueTotal();

        assertThat(total).isCloseTo(25000.0, org.assertj.core.data.Offset.offset(0.01));
    }

    // --- Preset date range extras ---

    @Test
    void setPresetDateRangeThisWeekSetsCorrectDates() {
        presenter.setPresetDateRange("THIS_WEEK");
        assertThat(presenter.getFromDate()).isNotNull();
        assertThat(presenter.getToDate()).isNotNull();
    }

    @Test
    void setPresetDateRangeUnknownPresetDefaultsToToday() {
        presenter.setPresetDateRange("UNKNOWN_PRESET");
        assertThat(presenter.getFromDate()).isNotNull();
        assertThat(presenter.getToDate()).isNotNull();
        assertThat(presenter.getFromDate()).isEqualTo(presenter.getToDate());
    }

    // --- generateReport dispatch ---

    @Test
    void generateReportDispatchesMargin() {
        presenter.setSelectedReportType("MARGIN");
        when(reportService.getMarginReport()).thenReturn(Collections.emptyList());

        Object result = presenter.generateReport();

        assertThat(result).isInstanceOf(List.class);
        verify(reportService).getMarginReport();
    }

    @Test
    void generateReportDispatchesSalesPeriod() {
        presenter.setSelectedReportType("SALES_PERIOD");
        presenter.setDateRange("01/07/2026", "31/07/2026");
        when(reportService.getSalesByPeriodReport("01/07/2026", "31/07/2026"))
                .thenReturn(Collections.emptyList());

        Object result = presenter.generateReport();

        assertThat(result).isInstanceOf(List.class);
        verify(reportService).getSalesByPeriodReport("01/07/2026", "31/07/2026");
    }

    @Test
    void generateReportDispatchesChannelComparison() {
        presenter.setSelectedReportType("CHANNEL");
        presenter.setDateRange("01/07/2026", "31/07/2026");
        when(reportService.getChannelComparisonReport("01/07/2026", "31/07/2026"))
                .thenReturn(Collections.emptyList());

        Object result = presenter.generateReport();

        assertThat(result).isInstanceOf(List.class);
        verify(reportService).getChannelComparisonReport("01/07/2026", "31/07/2026");
    }

    @Test
    void generateReportDispatchesRotation() {
        presenter.setSelectedReportType("ROTATION");
        presenter.setDateRange("01/07/2026", "31/07/2026");
        when(reportService.getRotationReport("01/07/2026", "31/07/2026"))
                .thenReturn(Collections.emptyList());

        Object result = presenter.generateReport();

        assertThat(result).isInstanceOf(List.class);
        verify(reportService).getRotationReport("01/07/2026", "31/07/2026");
    }

    @Test
    void generateReportDispatchesStockValue() {
        presenter.setSelectedReportType("STOCK_VALUE");
        when(reportService.getStockValueReport()).thenReturn(Collections.emptyList());

        Object result = presenter.generateReport();

        assertThat(result).isInstanceOf(List.class);
        verify(reportService).getStockValueReport();
    }

    @Test
    void generateReportWithBlankTypeThrowsException() {
        presenter.setSelectedReportType("   ");

        assertThatThrownBy(() -> presenter.generateReport())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo de reporte");
    }

    // --- Validation ---

    @Test
    void generateReportWithNullTypeThrowsException() {
        assertThatThrownBy(() -> presenter.generateReport())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo de reporte");
    }

    @Test
    void generateReportWithInvalidTypeThrowsException() {
        presenter.setSelectedReportType("INVALID");
        assertThatThrownBy(() -> presenter.generateReport())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    void generateReportWithMissingDateRangeThrowsForPeriodReports() {
        presenter.setSelectedReportType("SALES_PERIOD");
        assertThatThrownBy(() -> presenter.generateReport())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecha");
    }
}
