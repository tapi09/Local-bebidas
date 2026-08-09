package com.cocolatan.ui;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Lightweight non-blocking toast notifications for the main content area.
 *
 * <p>Decision (SDD change fix-audit-findings, open question resolution): the
 * existing {@code AlertService} only offers blocking dialogs, so backup
 * feedback uses a toast instead — a styled {@link Label} stacked on top of the
 * content area's {@link StackPane}, auto-removed after a short delay. It never
 * blocks the UI thread and never steals focus, unlike a separate TRANSPARENT
 * {@code Stage} (owner/focus problems on some platforms) or a blocking
 * {@code Alert(INFORMATION)}.</p>
 */
public final class ToastService {

    /** How long a toast stays visible by default. */
    public static final Duration DEFAULT_DURATION = Duration.seconds(3);

    private ToastService() {
        // Utility class — no instantiation
    }

    /**
     * Shows a toast over {@code parent} for the default duration.
     *
     * @param parent  the StackPane the toast is stacked onto (e.g. content area)
     * @param message the message text to display
     */
    public static void show(StackPane parent, String message) {
        show(parent, message, DEFAULT_DURATION);
    }

    /**
     * Shows a toast over {@code parent} for the given duration. The toast is
     * removed from the parent when the duration elapses.
     *
     * @param parent      the StackPane the toast is stacked onto
     * @param message     the message text to display
     * @param visibleFor  how long the toast stays visible
     */
    static void show(StackPane parent, String message, Duration visibleFor) {
        Label toast = new Label(message);
        toast.getStyleClass().add("toast");
        parent.getChildren().add(toast);

        PauseTransition pause = new PauseTransition(visibleFor);
        pause.setOnFinished(e -> parent.getChildren().remove(toast));
        pause.play();
    }
}
