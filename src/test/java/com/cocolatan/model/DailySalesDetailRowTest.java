package com.cocolatan.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DailySalesDetailRowTest {

    @Test
    void dailySalesDetailRowStoresAllFields() {
        DailySalesDetailRow row = new DailySalesDetailRow();
        row.setDate("03/08/2026");
        row.setProductName("Coca-Cola 500ml");
        row.setQuantity(3);
        row.setUnitPrice(600.0);
        row.setLineTotal(1800.0);

        assertThat(row.getDate()).isEqualTo("03/08/2026");
        assertThat(row.getProductName()).isEqualTo("Coca-Cola 500ml");
        assertThat(row.getQuantity()).isEqualTo(3);
        assertThat(row.getUnitPrice()).isEqualTo(600.0);
        assertThat(row.getLineTotal()).isEqualTo(1800.0);
    }

    @Test
    void dailySalesDetailRowDefaults() {
        DailySalesDetailRow row = new DailySalesDetailRow();

        assertThat(row.getDate()).isNull();
        assertThat(row.getProductName()).isNull();
        assertThat(row.getQuantity()).isEqualTo(0);
        assertThat(row.getUnitPrice()).isEqualTo(0.0);
        assertThat(row.getLineTotal()).isEqualTo(0.0);
    }

    @Test
    void dailySalesDetailRowLineTotalCalculatedCorrectly() {
        DailySalesDetailRow row = new DailySalesDetailRow();
        row.setQuantity(5);
        row.setUnitPrice(250.0);
        row.setLineTotal(row.getQuantity() * row.getUnitPrice());

        assertThat(row.getLineTotal()).isEqualTo(1250.0);
    }
}