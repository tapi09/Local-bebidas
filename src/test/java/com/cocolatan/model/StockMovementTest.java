package com.cocolatan.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementTest {

    @Test
    void createStockMovementWithAllFields() {
        StockMovement movement = new StockMovement();
        movement.setId(1L);
        movement.setProductId(5L);
        movement.setMovementType("ENTRY");
        movement.setQuantity(24);
        movement.setReferenceType("PURCHASE");
        movement.setReferenceId(1L);
        movement.setNotes("Initial stock");

        assertThat(movement.getId()).isEqualTo(1L);
        assertThat(movement.getProductId()).isEqualTo(5L);
        assertThat(movement.getMovementType()).isEqualTo("ENTRY");
        assertThat(movement.getQuantity()).isEqualTo(24);
        assertThat(movement.getReferenceType()).isEqualTo("PURCHASE");
        assertThat(movement.getReferenceId()).isEqualTo(1L);
        assertThat(movement.getNotes()).isEqualTo("Initial stock");
    }

    @Test
    void stockMovementWithNullOptionalFields() {
        StockMovement movement = new StockMovement();
        movement.setProductId(1L);
        movement.setMovementType("EXIT");
        movement.setQuantity(5);
        assertThat(movement.getReferenceType()).isNull();
        assertThat(movement.getReferenceId()).isNull();
        assertThat(movement.getNotes()).isNull();
    }
}
