package com.cocolatan.view;

import com.cocolatan.model.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaleControllerTest {

    private Product product(String name, String category, String barcode) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setBarcode(barcode);
        return p;
    }

    @Test
    void matchesQueryMatchesByCategory() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("gaseosa", p)).isTrue();
    }

    @Test
    void matchesQueryMatchesByName() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("coca", p)).isTrue();
    }

    @Test
    void matchesQueryMatchesByBarcode() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("1234567", p)).isTrue();
    }

    @Test
    void matchesQueryIsCaseInsensitive() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("GASEOSA", p)).isTrue();
        assertThat(SaleController.matchesQuery("COCA", p)).isTrue();
    }

    @Test
    void matchesQueryWithEmptyQueryReturnsTrue() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("", p)).isTrue();
        assertThat(SaleController.matchesQuery("   ", p)).isTrue();
    }

    @Test
    void matchesQueryWithNullQueryReturnsTrue() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery(null, p)).isTrue();
    }

    @Test
    void matchesQueryNoMatchReturnsFalse() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("vino", p)).isFalse();
    }

    @Test
    void matchesQueryWithNullCategoryDoesNotThrow() {
        Product p = product("Coca-Cola 500ml", null, null);

        assertThat(SaleController.matchesQuery("gaseosa", p)).isFalse();
        assertThat(SaleController.matchesQuery("coca", p)).isTrue();
    }

    @Test
    void matchesQueryMatchesBySubcategory() {
        Product p = product("Quilmes 1L", "Cervezas", "7790001234567");
        p.setSubcategoryName("Latas");

        assertThat(SaleController.matchesQuery("latas", p)).isTrue();
        assertThat(SaleController.matchesQuery("LATAS", p)).isTrue();
    }

    @Test
    void matchesQueryWithNullSubcategoryDoesNotThrow() {
        Product p = product("Quilmes 1L", "Cervezas", "7790001234567");

        assertThat(SaleController.matchesQuery("latas", p)).isFalse();
        assertThat(SaleController.matchesQuery("quilmes", p)).isTrue();
    }
}
