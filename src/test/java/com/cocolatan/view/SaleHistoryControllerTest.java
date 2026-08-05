package com.cocolatan.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaleHistoryControllerTest {

    // --- formatCustomerLabel ---

    @Test
    void formatCustomerLabelWithId() {
        assertThat(SaleHistoryController.formatCustomerLabel(12L)).isEqualTo("Cliente #12");
    }

    @Test
    void formatCustomerLabelForCounterSale() {
        assertThat(SaleHistoryController.formatCustomerLabel(null)).isEqualTo("Mostrador");
    }

    // --- formatChannel ---

    @Test
    void formatChannelMapsLocal() {
        assertThat(SaleHistoryController.formatChannel("IN")).isEqualTo("Local");
    }

    @Test
    void formatChannelTreatsEverythingElseAsPedidosYa() {
        assertThat(SaleHistoryController.formatChannel("PEDIDOSYA")).isEqualTo("PedidosYa");
        assertThat(SaleHistoryController.formatChannel("OTHER")).isEqualTo("PedidosYa");
        assertThat(SaleHistoryController.formatChannel(null)).isEqualTo("PedidosYa");
    }

    // --- formatPaymentMethod ---

    @Test
    void formatPaymentMethodMapsInternalValues() {
        assertThat(SaleHistoryController.formatPaymentMethod("CASH")).isEqualTo("Efectivo");
        assertThat(SaleHistoryController.formatPaymentMethod("CREDIT_CARD")).isEqualTo("Tarjeta Crédito");
        assertThat(SaleHistoryController.formatPaymentMethod("DEBIT_CARD")).isEqualTo("Tarjeta Débito");
        assertThat(SaleHistoryController.formatPaymentMethod("TRANSFER")).isEqualTo("Transferencia");
        assertThat(SaleHistoryController.formatPaymentMethod("MIXED")).isEqualTo("Mixto");
    }

    @Test
    void formatPaymentMethodPassesThroughUnknownValue() {
        assertThat(SaleHistoryController.formatPaymentMethod("OTHER")).isEqualTo("OTHER");
    }

    // --- formatReceipt ---

    @Test
    void formatReceiptReturnsDashWhenNull() {
        assertThat(SaleHistoryController.formatReceipt(null)).isEqualTo("—");
    }

    @Test
    void formatReceiptReturnsDashWhenBlank() {
        assertThat(SaleHistoryController.formatReceipt("")).isEqualTo("—");
        assertThat(SaleHistoryController.formatReceipt("   \n  ")).isEqualTo("—");
    }

    @Test
    void formatReceiptCollapsesLinesToSingleLine() {
        assertThat(SaleHistoryController.formatReceipt("Fecha: 05/08/2026\nPago: Efectivo"))
                .isEqualTo("Fecha: 05/08/2026 Pago: Efectivo");
    }

    @Test
    void formatReceiptTruncatesLongText() {
        String longText = "A".repeat(100);
        assertThat(SaleHistoryController.formatReceipt(longText))
                .isEqualTo("A".repeat(80) + "…");
    }

    @Test
    void formatReceiptKeepsShortTextUntruncated() {
        String shortText = "Comprobante #42";
        assertThat(SaleHistoryController.formatReceipt(shortText)).isEqualTo(shortText);
    }
}
