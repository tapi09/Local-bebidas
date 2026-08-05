package com.cocolatan.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseTest {

    @Test
    void createPurchaseWithAllFields() {
        Purchase purchase = new Purchase();
        purchase.setId(1L);
        purchase.setSupplierId(2L);
        purchase.setInvoiceRef("INV-001");
        purchase.setPurchaseDate("22/07/2026");
        purchase.setSubtotal(7000.0);
        purchase.setTaxAmount(1470.0);
        purchase.setTotalAmount(8470.0);
        purchase.setNotes("First order");

        assertThat(purchase.getId()).isEqualTo(1L);
        assertThat(purchase.getSupplierId()).isEqualTo(2L);
        assertThat(purchase.getInvoiceRef()).isEqualTo("INV-001");
        assertThat(purchase.getPurchaseDate()).isEqualTo("22/07/2026");
        assertThat(purchase.getSubtotal()).isEqualTo(7000.0);
        assertThat(purchase.getTaxAmount()).isEqualTo(1470.0);
        assertThat(purchase.getTotalAmount()).isEqualTo(8470.0);
        assertThat(purchase.getNotes()).isEqualTo("First order");
    }

    @Test
    void purchaseWithNullOptionalFields() {
        Purchase purchase = new Purchase();
        purchase.setSupplierId(1L);
        purchase.setPurchaseDate("22/07/2026");
        assertThat(purchase.getInvoiceRef()).isNull();
        assertThat(purchase.getNotes()).isNull();
        assertThat(purchase.getSubtotal()).isEqualTo(0.0);
        assertThat(purchase.getTaxAmount()).isEqualTo(0.0);
    }
}
