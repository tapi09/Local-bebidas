package com.softwaredebebidas.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertControllerTest {

    // --- formatCountLabel ---

    @Test
    void formatCountLabelBuildsLabel() {
        assertThat(AlertController.formatCountLabel("Vencimiento", 3)).isEqualTo("Vencimiento: 3");
        assertThat(AlertController.formatCountLabel("Stock Bajo", 0)).isEqualTo("Stock Bajo: 0");
    }

    @Test
    void formatCountLabelWithTotal() {
        assertThat(AlertController.formatCountLabel("Total", 7)).isEqualTo("Total: 7");
    }

    // --- totalAlertCount ---

    @Test
    void totalAlertCountSumsBothCounts() {
        assertThat(AlertController.totalAlertCount(3, 4)).isEqualTo(7);
        assertThat(AlertController.totalAlertCount(0, 0)).isEqualTo(0);
    }
}
