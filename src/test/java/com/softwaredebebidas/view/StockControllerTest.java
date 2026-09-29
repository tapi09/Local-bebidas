package com.softwaredebebidas.view;

import com.softwaredebebidas.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockControllerTest {

    private Product product(Long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        return p;
    }

    // --- mapStatusFilter ---

    @Test
    void mapStatusFilterMapsDisplayLabels() {
        assertThat(StockController.mapStatusFilter("BAJO")).isEqualTo("LOW");
        assertThat(StockController.mapStatusFilter("SIN STOCK")).isEqualTo("OUT");
    }

    @Test
    void mapStatusFilterPassesThroughInternalValue() {
        assertThat(StockController.mapStatusFilter("OK")).isEqualTo("OK");
    }

    // --- resolveMovementProductName ---

    @Test
    void resolveMovementProductNameReturnsMatchingName() {
        List<Product> products = List.of(product(1L, "Coca-Cola 500ml"), product(2L, "Sprite 500ml"));
        assertThat(StockController.resolveMovementProductName(products, 2L)).isEqualTo("Sprite 500ml");
    }

    @Test
    void resolveMovementProductNameFallsBackToIdWhenMissing() {
        List<Product> products = List.of(product(1L, "Coca-Cola 500ml"));
        assertThat(StockController.resolveMovementProductName(products, 99L)).isEqualTo("99");
    }

    @Test
    void resolveMovementProductNameWithNullListFallsBackToId() {
        assertThat(StockController.resolveMovementProductName(null, 5L)).isEqualTo("5");
    }

    @Test
    void resolveMovementProductNameWithEmptyListFallsBackToId() {
        assertThat(StockController.resolveMovementProductName(List.of(), 5L)).isEqualTo("5");
    }

    // --- formatMovementReference ---

    @Test
    void formatMovementReferenceWithId() {
        assertThat(StockController.formatMovementReference("SALE", 12L)).isEqualTo("SALE #12");
    }

    @Test
    void formatMovementReferenceWithoutId() {
        assertThat(StockController.formatMovementReference("ADJUSTMENT", null)).isEqualTo("ADJUSTMENT");
    }

    @Test
    void formatMovementReferenceEmptyTypeWithId() {
        assertThat(StockController.formatMovementReference("", 3L)).isEqualTo(" #3");
    }

    // --- validateAdjustmentQuantity ---

    @Test
    void validateAdjustmentQuantityAcceptsValidValue() {
        assertThat(StockController.validateAdjustmentQuantity("5")).isNull();
        assertThat(StockController.validateAdjustmentQuantity("-5")).isNull();
    }

    @Test
    void validateAdjustmentQuantityRejectsBlank() {
        assertThat(StockController.validateAdjustmentQuantity("")).isEqualTo("Ingrese una cantidad.");
        assertThat(StockController.validateAdjustmentQuantity("   ")).isEqualTo("Ingrese una cantidad.");
        assertThat(StockController.validateAdjustmentQuantity(null)).isEqualTo("Ingrese una cantidad.");
    }

    @Test
    void validateAdjustmentQuantityRejectsZero() {
        assertThat(StockController.validateAdjustmentQuantity("0")).isEqualTo("La cantidad no puede ser cero.");
    }

    @Test
    void validateAdjustmentQuantityRejectsNonNumeric() {
        assertThat(StockController.validateAdjustmentQuantity("abc"))
                .isEqualTo("Ingrese un valor numérico válido para la cantidad.");
    }
}
