package com.softwaredebebidas.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailySalesDetailReportTest {

    @Test
    void dailySalesDetailReportStoresAllFields() {
        DailySalesDetailRow row = new DailySalesDetailRow();
        row.setDate("03/08/2026");
        row.setProductName("Coca-Cola");
        row.setQuantity(3);
        row.setUnitPrice(600.0);
        row.setLineTotal(1800.0);

        DailySalesDetailReport report = new DailySalesDetailReport();
        report.setRows(Arrays.asList(row));
        report.setGrandTotal(1800.0);
        report.setFromDate("01/08/2026");
        report.setToDate("05/08/2026");

        assertThat(report.getRows()).hasSize(1);
        assertThat(report.getGrandTotal()).isEqualTo(1800.0);
        assertThat(report.getFromDate()).isEqualTo("01/08/2026");
        assertThat(report.getToDate()).isEqualTo("05/08/2026");
    }

    @Test
    void dailySalesDetailReportDefaults() {
        DailySalesDetailReport report = new DailySalesDetailReport();

        assertThat(report.getRows()).isNull();
        assertThat(report.getGrandTotal()).isEqualTo(0.0);
        assertThat(report.getFromDate()).isNull();
        assertThat(report.getToDate()).isNull();
    }

    @Test
    void dailySalesDetailReportWithEmptyRows() {
        DailySalesDetailReport report = new DailySalesDetailReport();
        report.setRows(Collections.emptyList());
        report.setGrandTotal(0.0);

        assertThat(report.getRows()).isEmpty();
        assertThat(report.getGrandTotal()).isEqualTo(0.0);
    }
}