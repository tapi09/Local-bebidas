package com.softwaredebebidas.ui;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the lightweight non-blocking toast overlay (SDD fix-audit-findings
 * open question resolution: option (a) — overlay Label on the content StackPane
 * with auto-close timer, instead of a blocking Alert or an extra TRANSPARENT
 * Stage).
 */
class ToastServiceTest {

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (Exception e) {
            // already started or headless
        }
    }

    @Test
    void showAddsToastLabelWithMessageToParent() {
        StackPane parent = new StackPane();
        parent.getChildren().add(new Label("existing-view"));

        ToastService.show(parent, "Respaldo exportado");

        assertThat(parent.getChildren()).hasSize(2);
        Label toast = (Label) parent.getChildren().get(1);
        assertThat(toast.getText()).isEqualTo("Respaldo exportado");
        assertThat(toast.getStyleClass()).contains("toast");
    }

    @Test
    void toastAutoRemovesAfterConfiguredDuration() throws Exception {
        StackPane parent = new StackPane();

        ToastService.show(parent, "Mensaje temporal", Duration.millis(300));

        assertThat(parent.getChildren()).hasSize(1);
        Thread.sleep(900);
        assertThat(parent.getChildren()).isEmpty();
    }

    @Test
    void toastRemovalLeavesExistingChildrenUntouched() throws Exception {
        StackPane parent = new StackPane();
        Label existing = new Label("vista");
        parent.getChildren().add(existing);

        ToastService.show(parent, "Temporal", Duration.millis(300));
        Thread.sleep(900);

        assertThat(parent.getChildren()).containsExactly(existing);
    }
}
