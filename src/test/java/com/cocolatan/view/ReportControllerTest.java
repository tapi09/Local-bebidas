package com.cocolatan.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportControllerTest {

    // --- mapReportType ---

    @Test
    void mapReportTypeMapsDisplayLabels() {
        assertThat(ReportController.mapReportType("Margen por Producto")).isEqualTo("MARGIN");
        assertThat(ReportController.mapReportType("Ventas por Período")).isEqualTo("SALES_PERIOD");
        assertThat(ReportController.mapReportType("Comparación de Canales")).isEqualTo("CHANNEL");
        assertThat(ReportController.mapReportType("Rotación de Stock")).isEqualTo("ROTATION");
        assertThat(ReportController.mapReportType("Valor de Stock")).isEqualTo("STOCK_VALUE");
        assertThat(ReportController.mapReportType("Productos Más Vendidos")).isEqualTo("TOP_SELLERS");
    }

    @Test
    void mapReportTypePassesThroughUnknownValue() {
        assertThat(ReportController.mapReportType("Custom")).isEqualTo("Custom");
    }

    // --- mapPreset ---

    @Test
    void mapPresetMapsDisplayLabels() {
        assertThat(ReportController.mapPreset("Hoy")).isEqualTo("TODAY");
        assertThat(ReportController.mapPreset("Esta Semana")).isEqualTo("THIS_WEEK");
        assertThat(ReportController.mapPreset("Este Mes")).isEqualTo("THIS_MONTH");
        assertThat(ReportController.mapPreset("Últimos 30 Días")).isEqualTo("LAST_30_DAYS");
    }

    @Test
    void mapPresetUnknownValueDefaultsToToday() {
        assertThat(ReportController.mapPreset("Custom")).isEqualTo("TODAY");
    }

    // --- formatChannel ---

    @Test
    void formatChannelMapsInternalValues() {
        assertThat(ReportController.formatChannel("IN")).isEqualTo("Local");
        assertThat(ReportController.formatChannel("PEDIDOSYA")).isEqualTo("PedidosYa");
    }

    @Test
    void formatChannelPassesThroughUnknownValue() {
        assertThat(ReportController.formatChannel("OTHER")).isEqualTo("OTHER");
    }

    // --- needsDateRange ---

    @Test
    void needsDateRangeIsFalseForSummaryReports() {
        assertThat(ReportController.needsDateRange("Margen por Producto")).isFalse();
        assertThat(ReportController.needsDateRange("Valor de Stock")).isFalse();
        assertThat(ReportController.needsDateRange("Productos Más Vendidos")).isFalse();
    }

    @Test
    void needsDateRangeIsTrueForPeriodReports() {
        assertThat(ReportController.needsDateRange("Ventas por Período")).isTrue();
        assertThat(ReportController.needsDateRange("Comparación de Canales")).isTrue();
        assertThat(ReportController.needsDateRange("Rotación de Stock")).isTrue();
    }

    // --- formatMarginPercent ---

    @Test
    void formatMarginPercentShowsNaWhenZeroCost() {
        assertThat(ReportController.formatMarginPercent(true, 50.0)).isEqualTo("N/A");
    }

    @Test
    void formatMarginPercentFormatsValue() {
        assertThat(ReportController.formatMarginPercent(false, 50.0))
                .isEqualTo(String.format("%.1f%%", 50.0));
        assertThat(ReportController.formatMarginPercent(false, 12.345))
                .isEqualTo(String.format("%.1f%%", 12.345));
    }
}
