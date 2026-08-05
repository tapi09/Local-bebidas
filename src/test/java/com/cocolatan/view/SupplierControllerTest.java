package com.cocolatan.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierControllerTest {

    // --- isValidSupplierName ---

    @Test
    void isValidSupplierNameAcceptsNonBlankName() {
        assertThat(SupplierController.isValidSupplierName("Distribuidora Norte")).isTrue();
    }

    @Test
    void isValidSupplierNameRejectsBlank() {
        assertThat(SupplierController.isValidSupplierName("")).isFalse();
        assertThat(SupplierController.isValidSupplierName("   ")).isFalse();
    }

    @Test
    void isValidSupplierNameRejectsNull() {
        assertThat(SupplierController.isValidSupplierName(null)).isFalse();
    }
}
