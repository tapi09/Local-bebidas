package com.softwaredebebidas.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseInvoicePhotoTest {

    @Test
    void purchaseStoresAndRetrievesInvoicePhotoPath() {
        Purchase purchase = new Purchase();
        purchase.setInvoicePhotoPath("purchase-invoices/abc-123.jpg");

        assertThat(purchase.getInvoicePhotoPath()).isEqualTo("purchase-invoices/abc-123.jpg");
    }

    @Test
    void purchaseInvoicePhotoPathDefaultsToNull() {
        Purchase purchase = new Purchase();

        assertThat(purchase.getInvoicePhotoPath()).isNull();
    }

    @Test
    void purchaseInvoicePhotoPathCanBeUpdated() {
        Purchase purchase = new Purchase();
        purchase.setInvoicePhotoPath("purchase-invoices/first.jpg");
        purchase.setInvoicePhotoPath("purchase-invoices/second.jpg");

        assertThat(purchase.getInvoicePhotoPath()).isEqualTo("purchase-invoices/second.jpg");
    }
}