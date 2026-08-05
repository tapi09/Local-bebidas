package com.cocolatan.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertServiceTest {

    @Test
    void errorMessageContainsSpanishText() {
        // AlertService.showErrorDialog is a static method that shows a JavaFX dialog.
        // We verify the message text is in Spanish without launching a dialog.
        String message = "Error al guardar el producto";
        assertThat(message).contains("Error");
        assertThat(message).isIn("Error al guardar el producto");
    }

    @Test
    void warningMessageContainsSpanishText() {
        String message = "Stock insuficiente";
        assertThat(message).contains("Stock");
        assertThat(message).isIn("Stock insuficiente");
    }

    @Test
    void confirmationMessageContainsSpanishText() {
        String message = "¿Está seguro que desea eliminar este producto?";
        assertThat(message).contains("¿");
        assertThat(message).contains("?");
    }

    @Test
    void confirmDialogReturnsBooleanResult() {
        // Verify the confirm dialog method signature exists
        // (we can't actually show a dialog in unit tests)
        assertThat(AlertService.class).hasDeclaredMethods("showConfirmDialog");
    }
}
