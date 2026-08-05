package com.cocolatan.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaleTest {

    @Test
    void createSaleWithAllFields() {
        Sale sale = new Sale();
        sale.setId(1L);
        sale.setSaleDate("22/07/2026");
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");
        sale.setTotalAmount(1200.0);

        assertThat(sale.getId()).isEqualTo(1L);
        assertThat(sale.getSaleDate()).isEqualTo("22/07/2026");
        assertThat(sale.getChannel()).isEqualTo("IN");
        assertThat(sale.getPaymentMethod()).isEqualTo("CASH");
        assertThat(sale.getTotalAmount()).isEqualTo(1200.0);
    }
}
