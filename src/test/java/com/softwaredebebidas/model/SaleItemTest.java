package com.softwaredebebidas.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaleItemTest {

    @Test
    void createSaleItemWithAllFields() {
        SaleItem item = new SaleItem();
        item.setId(1L);
        item.setSaleId(1L);
        item.setProductId(5L);
        item.setQuantity(2);
        item.setUnitPrice(600.0);
        item.setSubtotal(1200.0);

        assertThat(item.getId()).isEqualTo(1L);
        assertThat(item.getSaleId()).isEqualTo(1L);
        assertThat(item.getProductId()).isEqualTo(5L);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getUnitPrice()).isEqualTo(600.0);
        assertThat(item.getSubtotal()).isEqualTo(1200.0);
    }
}
