package com.cocolatan.view;

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
}
