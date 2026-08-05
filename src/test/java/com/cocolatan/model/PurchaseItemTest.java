package com.cocolatan.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseItemTest {

    @Test
    void createPurchaseItemWithAllFields() {
        PurchaseItem item = new PurchaseItem();
        item.setId(1L);
        item.setPurchaseId(1L);
        item.setProductId(5L);
        item.setQuantity(24);
        item.setUnitCost(350.0);
        item.setLotNumber("LOT-2026-001");
        item.setExpiryDate("31/12/2026");

        assertThat(item.getId()).isEqualTo(1L);
        assertThat(item.getPurchaseId()).isEqualTo(1L);
        assertThat(item.getProductId()).isEqualTo(5L);
        assertThat(item.getQuantity()).isEqualTo(24);
        assertThat(item.getUnitCost()).isEqualTo(350.0);
        assertThat(item.getLotNumber()).isEqualTo("LOT-2026-001");
        assertThat(item.getExpiryDate()).isEqualTo("31/12/2026");
    }

    @Test
    void purchaseItemWithNullOptionalFields() {
        PurchaseItem item = new PurchaseItem();
        item.setPurchaseId(1L);
        item.setProductId(1L);
        item.setQuantity(10);
        item.setUnitCost(100.0);
        assertThat(item.getLotNumber()).isNull();
        assertThat(item.getExpiryDate()).isNull();
    }
}
