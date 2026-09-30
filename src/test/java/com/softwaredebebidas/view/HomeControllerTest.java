package com.softwaredebebidas.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HomeControllerTest {

    // --- formatChannel ---

    @Test
    void formatChannelMapsLocal() {
        assertThat(HomeController.formatChannel("IN")).isEqualTo("Local");
    }

    @Test
    void formatChannelTreatsEverythingElseAsPedidosYa() {
        assertThat(HomeController.formatChannel("PEDIDOSYA")).isEqualTo("PedidosYa");
        assertThat(HomeController.formatChannel("OTHER")).isEqualTo("PedidosYa");
        assertThat(HomeController.formatChannel(null)).isEqualTo("PedidosYa");
    }

    // --- formatSaleDate ---

    @Test
    void formatSaleDateConvertsIsoToDisplay() {
        assertThat(HomeController.formatSaleDate("2026-09-14")).isEqualTo("14/09/2026");
    }

    @Test
    void formatSaleDateReturnsEmptyForNull() {
        assertThat(HomeController.formatSaleDate(null)).isEmpty();
    }

    @Test
    void formatSaleDateReturnsEmptyForBlank() {
        assertThat(HomeController.formatSaleDate("  ")).isEmpty();
    }

    // --- formatPayment ---

    @Test
    void formatPaymentUsesFriendlyLabels() {
        assertThat(HomeController.formatPayment("CASH")).isEqualTo("Efectivo");
        assertThat(HomeController.formatPayment("MIXED")).isEqualTo("Mixto");
    }

    @Test
    void formatPaymentReturnsEmptyForNull() {
        assertThat(HomeController.formatPayment(null)).isEmpty();
    }
}
