package com.softwaredebebidas.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    void createProductWithAllFields() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Coca-Cola 500ml");
        product.setCategory("Gaseosa");
        product.setPresentation("Botella");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        product.setSupplierId(1L);
        product.setBarcode("7790001234567");
        product.setMinStock(10);
        product.setActive(true);

        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("Coca-Cola 500ml");
        assertThat(product.getCategory()).isEqualTo("Gaseosa");
        assertThat(product.getPresentation()).isEqualTo("Botella");
        assertThat(product.getCostPrice()).isEqualTo(350.0);
        assertThat(product.getSalePrice()).isEqualTo(600.0);
        assertThat(product.getSupplierId()).isEqualTo(1L);
        assertThat(product.getBarcode()).isEqualTo("7790001234567");
        assertThat(product.getMinStock()).isEqualTo(10);
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void defaultProductIsActive() {
        Product product = new Product();
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void defaultMinStockIsZero() {
        Product product = new Product();
        assertThat(product.getMinStock()).isEqualTo(0);
    }

    @Test
    void hierarchyFieldsAreSettableAndGettable() {
        Product product = new Product();
        product.setCategoryId(1L);
        product.setSubcategoryId(2L);
        product.setCategoryName("Cervezas");
        product.setSubcategoryName("Latas");

        assertThat(product.getCategoryId()).isEqualTo(1L);
        assertThat(product.getSubcategoryId()).isEqualTo(2L);
        assertThat(product.getCategoryName()).isEqualTo("Cervezas");
        assertThat(product.getSubcategoryName()).isEqualTo("Latas");
    }

    @Test
    void getHierarchyLabelReturnsEmptyWhenNoHierarchy() {
        Product product = new Product();
        assertThat(product.getHierarchyLabel()).isEmpty();
    }

    @Test
    void getHierarchyLabelReturnsCategoryOnly() {
        Product product = new Product();
        product.setCategoryName("Cervezas");
        assertThat(product.getHierarchyLabel()).isEqualTo("Cervezas");
    }

    @Test
    void getHierarchyLabelJoinsCategoryAndSubcategory() {
        Product product = new Product();
        product.setCategoryName("Cervezas");
        product.setSubcategoryName("Latas");
        assertThat(product.getHierarchyLabel()).isEqualTo("Cervezas — Latas");
    }

    @Test
    void getHierarchyLabelUsesResolvedNamesNotLegacyCategory() {
        Product product = new Product();
        product.setCategory("Gaseosa");
        product.setCategoryName("Gaseosas");
        product.setSubcategoryName("Botella");
        assertThat(product.getHierarchyLabel()).isEqualTo("Gaseosas — Botella");
    }
}
