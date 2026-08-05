package com.cocolatan.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * Service for showing JavaFX alert dialogs with Spanish messages.
 * Provides error, warning, information, and confirmation dialogs.
 */
public final class AlertService {

    private AlertService() {
        // Utility class — no instantiation
    }

    /**
     * Shows an error dialog with the given title and message.
     *
     * @param title   dialog title
     * @param message error message in Spanish
     */
    public static void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows a warning dialog with the given title and message.
     *
     * @param title   dialog title
     * @param message warning message in Spanish
     */
    public static void showWarningDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an information dialog with the given title and message.
     *
     * @param title   dialog title
     * @param message information message in Spanish
     */
    public static void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows a confirmation dialog and returns whether the user confirmed.
     *
     * @param title   dialog title
     * @param message confirmation message in Spanish
     * @return true if the user clicked OK, false otherwise
     */
    public static boolean showConfirmDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
