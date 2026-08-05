package com.cocolatan.view;

import com.cocolatan.model.Product;
import com.cocolatan.model.Supplier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseControllerTest {

    private Product product(String name, String presentation, String barcode, String category) {
        Product p = new Product();
        p.setName(name);
        p.setPresentation(presentation);
        p.setBarcode(barcode);
        p.setCategory(category);
        return p;
    }

    private Supplier supplier(String name, String contact, String phone, String email) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setContact(contact);
        s.setPhone(phone);
        s.setEmail(email);
        return s;
    }

    // --- matchesProductFilter ---

    @Test
    void matchesProductFilterMatchesByName() {
        Product p = product("Coca-Cola 500ml", "Botella", "7790001234567", "Gaseosas");
        assertThat(PurchaseController.matchesProductFilter("coca", p)).isTrue();
    }

    @Test
    void matchesProductFilterMatchesByPresentation() {
        Product p = product("Coca-Cola 500ml", "Botella", "7790001234567", "Gaseosas");
        assertThat(PurchaseController.matchesProductFilter("botella", p)).isTrue();
    }

    @Test
    void matchesProductFilterMatchesByBarcode() {
        Product p = product("Coca-Cola 500ml", "Botella", "7790001234567", "Gaseosas");
        assertThat(PurchaseController.matchesProductFilter("1234567", p)).isTrue();
    }

    @Test
    void matchesProductFilterMatchesByCategory() {
        Product p = product("Coca-Cola 500ml", "Botella", "7790001234567", "Gaseosas");
        assertThat(PurchaseController.matchesProductFilter("gaseosas", p)).isTrue();
    }

    @Test
    void matchesProductFilterIsCaseInsensitive() {
        Product p = product("Coca-Cola 500ml", "Botella", "7790001234567", "Gaseosas");
        assertThat(PurchaseController.matchesProductFilter("BOTELLA", p)).isTrue();
        assertThat(PurchaseController.matchesProductFilter("COCA", p)).isTrue();
    }

    @Test
    void matchesProductFilterWithEmptyFilterReturnsTrue() {
        Product p = product("Coca-Cola 500ml", "Botella", "7790001234567", "Gaseosas");
        assertThat(PurchaseController.matchesProductFilter("", p)).isTrue();
        assertThat(PurchaseController.matchesProductFilter("   ", p)).isTrue();
    }

    @Test
    void matchesProductFilterWithNullFilterReturnsTrue() {
        Product p = product("Coca-Cola 500ml", "Botella", "7790001234567", "Gaseosas");
        assertThat(PurchaseController.matchesProductFilter(null, p)).isTrue();
    }

    @Test
    void matchesProductFilterNoMatchReturnsFalse() {
        Product p = product("Coca-Cola 500ml", "Botella", "7790001234567", "Gaseosas");
        assertThat(PurchaseController.matchesProductFilter("vino", p)).isFalse();
    }

    @Test
    void matchesProductFilterWithNullPresentationAndBarcodeDoesNotThrow() {
        Product p = product("Coca-Cola 500ml", null, null, null);
        assertThat(PurchaseController.matchesProductFilter("gaseosas", p)).isFalse();
        assertThat(PurchaseController.matchesProductFilter("coca", p)).isTrue();
    }

    // --- matchesSupplierFilter ---

    @Test
    void matchesSupplierFilterMatchesByName() {
        Supplier s = supplier("Distribuidora Norte", "Juan", "1155551234", "ventas@dnorte.com");
        assertThat(PurchaseController.matchesSupplierFilter("norte", s)).isTrue();
    }

    @Test
    void matchesSupplierFilterMatchesByContact() {
        Supplier s = supplier("Distribuidora Norte", "Juan", "1155551234", "ventas@dnorte.com");
        assertThat(PurchaseController.matchesSupplierFilter("juan", s)).isTrue();
    }

    @Test
    void matchesSupplierFilterMatchesByPhone() {
        Supplier s = supplier("Distribuidora Norte", "Juan", "1155551234", "ventas@dnorte.com");
        assertThat(PurchaseController.matchesSupplierFilter("5555", s)).isTrue();
    }

    @Test
    void matchesSupplierFilterMatchesByEmail() {
        Supplier s = supplier("Distribuidora Norte", "Juan", "1155551234", "ventas@dnorte.com");
        assertThat(PurchaseController.matchesSupplierFilter("dnorte.com", s)).isTrue();
    }

    @Test
    void matchesSupplierFilterIsCaseInsensitive() {
        Supplier s = supplier("Distribuidora Norte", "Juan", "1155551234", "ventas@dnorte.com");
        assertThat(PurchaseController.matchesSupplierFilter("NORTE", s)).isTrue();
    }

    @Test
    void matchesSupplierFilterWithEmptyFilterReturnsTrue() {
        Supplier s = supplier("Distribuidora Norte", "Juan", "1155551234", "ventas@dnorte.com");
        assertThat(PurchaseController.matchesSupplierFilter("", s)).isTrue();
        assertThat(PurchaseController.matchesSupplierFilter("   ", s)).isTrue();
        assertThat(PurchaseController.matchesSupplierFilter(null, s)).isTrue();
    }

    @Test
    void matchesSupplierFilterNoMatchReturnsFalse() {
        Supplier s = supplier("Distribuidora Norte", "Juan", "1155551234", "ventas@dnorte.com");
        assertThat(PurchaseController.matchesSupplierFilter("sur", s)).isFalse();
    }

    @Test
    void matchesSupplierFilterWithNullFieldsDoesNotThrow() {
        Supplier s = supplier("Distribuidora Norte", null, null, null);
        assertThat(PurchaseController.matchesSupplierFilter("distribuidora", s)).isTrue();
        assertThat(PurchaseController.matchesSupplierFilter("juan", s)).isFalse();
    }

    // --- parseTax ---

    @Test
    void parseTaxParsesValidNumber() {
        assertThat(PurchaseController.parseTax("21.5")).isEqualTo(21.5);
    }

    @Test
    void parseTaxTrimsWhitespace() {
        assertThat(PurchaseController.parseTax(" 10 ")).isEqualTo(10.0);
    }

    @Test
    void parseTaxWithBlankReturnsZero() {
        assertThat(PurchaseController.parseTax("")).isEqualTo(0.0);
        assertThat(PurchaseController.parseTax("   ")).isEqualTo(0.0);
    }

    @Test
    void parseTaxWithNullReturnsZero() {
        assertThat(PurchaseController.parseTax(null)).isEqualTo(0.0);
    }

    @Test
    void parseTaxWithInvalidTextReturnsZero() {
        assertThat(PurchaseController.parseTax("abc")).isEqualTo(0.0);
    }

    // --- resolveProductName ---

    @Test
    void resolveProductNameReturnsCachedName() {
        Map<Long, String> cache = Map.of(1L, "Coca-Cola 500ml");
        assertThat(PurchaseController.resolveProductName(cache, 1L)).isEqualTo("Coca-Cola 500ml");
    }

    @Test
    void resolveProductNameFallsBackToPlaceholder() {
        Map<Long, String> cache = Map.of(1L, "Coca-Cola 500ml");
        assertThat(PurchaseController.resolveProductName(cache, 42L)).isEqualTo("Producto #42");
    }

    @Test
    void resolveProductNameWithEmptyCacheFallsBack() {
        assertThat(PurchaseController.resolveProductName(Map.of(), 7L)).isEqualTo("Producto #7");
    }

    // --- validateItemFields ---

    @Test
    void validateItemFieldsAcceptsValidValues() {
        assertThat(PurchaseController.validateItemFields(2, 150.0)).isNull();
    }

    @Test
    void validateItemFieldsRejectsNonPositiveQuantity() {
        assertThat(PurchaseController.validateItemFields(0, 150.0))
                .isEqualTo("La cantidad debe ser mayor a 0.");
        assertThat(PurchaseController.validateItemFields(-3, 150.0))
                .isEqualTo("La cantidad debe ser mayor a 0.");
    }

    @Test
    void validateItemFieldsRejectsNegativeUnitCost() {
        assertThat(PurchaseController.validateItemFields(2, -1.0))
                .isEqualTo("El costo no puede ser negativo.");
    }

    @Test
    void validateItemFieldsAllowsZeroUnitCost() {
        assertThat(PurchaseController.validateItemFields(2, 0.0)).isNull();
    }
}
