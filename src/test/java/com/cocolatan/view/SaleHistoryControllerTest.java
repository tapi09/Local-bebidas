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
}
