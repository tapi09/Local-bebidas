package com.cocolatan.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierTest {

    @Test
    void createSupplierWithAllFields() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("Distribuidora Norte");
        supplier.setContact("Juan Perez");
        supplier.setPhone("+54 261 555-1234");
        supplier.setEmail("juan@distnorte.com");
        supplier.setAddress("Mendoza 1234");

        assertThat(supplier.getId()).isEqualTo(1L);
        assertThat(supplier.getName()).isEqualTo("Distribuidora Norte");
        assertThat(supplier.getContact()).isEqualTo("Juan Perez");
        assertThat(supplier.getPhone()).isEqualTo("+54 261 555-1234");
        assertThat(supplier.getEmail()).isEqualTo("juan@distnorte.com");
        assertThat(supplier.getAddress()).isEqualTo("Mendoza 1234");
    }

    @Test
    void supplierWithNullOptionalFields() {
        Supplier supplier = new Supplier();
        supplier.setName("Test Supplier");
        assertThat(supplier.getContact()).isNull();
        assertThat(supplier.getPhone()).isNull();
        assertThat(supplier.getEmail()).isNull();
        assertThat(supplier.getAddress()).isNull();
    }
}
