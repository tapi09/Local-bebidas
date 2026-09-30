package com.softwaredebebidas.view;

import com.softwaredebebidas.SoftwareDeBebidasApp;
import com.softwaredebebidas.model.User;
import com.softwaredebebidas.presenter.LoginPresenter;
import com.softwaredebebidas.repository.ConfigRepository;
import com.softwaredebebidas.repository.UserRepository;
import com.softwaredebebidas.service.AuthService;
import com.softwaredebebidas.util.AlertService;
import com.softwaredebebidas.util.LogoUtils;
import com.softwaredebebidas.util.VersionInfo;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    private static final long BRUTE_FORCE_DELAY_MS = 1500L;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Label loginLogoLabel;

    @FXML
    private ImageView loginLogoImage;

    @FXML
    private Label forgotPasswordLabel;

    @FXML
    private Label versionLabel;

    private LoginPresenter presenter;

    @FXML
    public void initialize() {
        presenter = new LoginPresenter(AuthService.getInstance());
        errorLabel.setVisible(false);

        if (versionLabel != null) {
            versionLabel.setText(versionFooterText(VersionInfo.getVersion()));
        }

        // Load business name for login logo
        try {
            ConfigRepository configRepo = new ConfigRepository(
                    com.softwaredebebidas.SoftwareDeBebidasApp.getDatabaseManager());
            loginLogoLabel.setText(configRepo.getBusinessName());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load business name", e);
        }

        loadLogo();

        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onLogin();
            }
        });
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                passwordField.requestFocus();
            }
        });
    }

    /**
     * Shows the business logo when one is configured, falling back to the
     * business-name label otherwise. Never throws.
     */
    private void loadLogo() {
        Image image = LogoUtils.loadLogoImage();
        if (image != null) {
            loginLogoImage.setImage(image);
            loginLogoImage.setVisible(true);
            loginLogoImage.setManaged(true);
            loginLogoLabel.setVisible(false);
            loginLogoLabel.setManaged(false);
            return;
        }
        loginLogoImage.setVisible(false);
        loginLogoImage.setManaged(false);
    }

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        String validationError = validateCredentials(username, password);
        if (validationError != null) {
            showError(validationError);
            if (username.isEmpty()) {
                usernameField.requestFocus();
            } else {
                passwordField.requestFocus();
            }
            return;
        }

        User user = presenter.login(username, password);
        if (user != null) {
            PasswordChangeInput changeInput = null;
            if (user.isMustChangePassword()) {
                changeInput = showPasswordChangeDialog().orElse(null);
            }
            PasswordChangeResult result = evaluatePasswordChange(presenter, user, changeInput);
            if (result.proceed()) {
                SoftwareDeBebidasApp.showMainView();
            } else if (result.errorMessage() != null) {
                AlertService.showErrorDialog("Error", result.errorMessage());
            }
        } else {
            showError("Usuario o contraseña incorrectos.");
            passwordField.clear();
            passwordField.requestFocus();
        }
    }

    /**
     * Opens the password recovery flow from the login screen. Requires a
     * configured provider master key; otherwise it tells the user to contact
     * the provider and stops.
     */
    @FXML
    private void onForgotPassword() {
        if (!presenter.isRecoveryConfigured()) {
            AlertService.showWarningDialog(
                    "Recuperación de Contraseña",
                    "La recuperación no está configurada. Contacte al proveedor.");
            return;
        }
        showRecoveryDialog().ifPresent(input -> {
            RecoveryResult result = evaluateRecovery(presenter, input);
            if (result.success()) {
                AlertService.showInfoDialog("Éxito", "Contraseña restablecida. Ya puede ingresar.");
            } else {
                if (result.wrongKey()) {
                    applyBruteForceDelay();
                }
                AlertService.showErrorDialog("Error", result.errorMessage());
            }
        });
    }

    /**
     * Shows the password recovery dialog (user selector, master key, new
     * password and confirmation). Returns the captured values, or empty when
     * the user cancels or no users are available.
     */
    private Optional<RecoveryInput> showRecoveryDialog() {
        List<User> users = loadUsersForRecovery();
        if (users == null) {
            return Optional.empty();
        }
        if (users.isEmpty()) {
            AlertService.showWarningDialog(
                    "Recuperación de Contraseña",
                    "No hay usuarios disponibles para restablecer.");
            return Optional.empty();
        }

        Dialog<RecoveryInput> dialog = new Dialog<>();
        dialog.setTitle("Recuperar Contraseña");
        dialog.setHeaderText("Ingrese la clave de recuperación del proveedor y la nueva contraseña.");

        ButtonType resetButtonType = new ButtonType("Restablecer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(resetButtonType, ButtonType.CANCEL);

        ComboBox<User> userBox = new ComboBox<>();
        userBox.getItems().setAll(users);
        userBox.setValue(users.get(0));
        userBox.setConverter(userConverter());

        PasswordField keyField = new PasswordField();
        keyField.setPromptText("Clave de recuperación");
        PasswordField newField = new PasswordField();
        newField.setPromptText("Contraseña nueva");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirmar contraseña nueva");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Usuario:"), 0, 0);
        grid.add(userBox, 1, 0);
        grid.add(new Label("Clave de recuperación:"), 0, 1);
        grid.add(keyField, 1, 1);
        grid.add(new Label("Contraseña nueva:"), 0, 2);
        grid.add(newField, 1, 2);
        grid.add(new Label("Confirmar:"), 0, 3);
        grid.add(confirmField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == resetButtonType) {
                User selected = userBox.getValue();
                return new RecoveryInput(
                        selected == null ? null : selected.getId(),
                        keyField.getText(), newField.getText(), confirmField.getText());
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private List<User> loadUsersForRecovery() {
        try {
            return new UserRepository(SoftwareDeBebidasApp.getDatabaseManager()).findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "No se pudieron cargar los usuarios para recuperación", e);
            AlertService.showErrorDialog("Error", "No se pudo cargar la lista de usuarios.");
            return null;
        }
    }

    private StringConverter<User> userConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(User user) {
                return user == null ? "" : user.getUsername();
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        };
    }

    /**
     * Small anti brute-force delay applied after a wrong master key, before the
     * user can try again.
     */
    private void applyBruteForceDelay() {
        try {
            Thread.sleep(BRUTE_FORCE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Applies the password recovery flow for the given input.
     * <p>
     * Returns a result indicating success or the reason for rejection. The
     * wrong-key case is flagged separately so callers can apply an anti
     * brute-force delay without revealing whether the user exists.
     */
    static RecoveryResult evaluateRecovery(LoginPresenter presenter, RecoveryInput input) {
        if (!presenter.isRecoveryConfigured()) {
            return RecoveryResult.notConfigured();
        }
        String validationError = validateRecoveryInput(input);
        if (validationError != null) {
            return RecoveryResult.failed(validationError);
        }
        if (!presenter.verifyMasterKey(input.masterKey())) {
            return RecoveryResult.wrongKeyResult();
        }
        try {
            presenter.resetPassword(input.userId(), input.newPassword());
            return RecoveryResult.successResult();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "No se pudo restablecer la contraseña", e);
            return RecoveryResult.failed("No se pudo restablecer la contraseña.");
        }
    }

    static String validateRecoveryInput(RecoveryInput input) {
        if (input == null) {
            return "Ingrese todos los datos.";
        }
        if (input.userId() == null) {
            return "Seleccione un usuario.";
        }
        if (input.masterKey() == null || input.masterKey().isEmpty()) {
            return "Ingrese la clave de recuperación.";
        }
        if (input.newPassword() == null || input.newPassword().isEmpty()) {
            return "Ingrese una contraseña nueva.";
        }
        if (input.confirm() == null || input.confirm().isEmpty()) {
            return "Confirme la contraseña nueva.";
        }
        if (!input.newPassword().equals(input.confirm())) {
            return "Las contraseñas no coinciden.";
        }
        return null;
    }

    /**
     * Shows a dialog requesting the current password, a new password and a
     * confirmation. Returns the captured values, or empty when the user cancels.
     */
    private Optional<PasswordChangeInput> showPasswordChangeDialog() {
        Dialog<PasswordChangeInput> dialog = new Dialog<>();
        dialog.setTitle("Cambiar Contraseña");
        dialog.setHeaderText("Debe cambiar su contraseña antes de continuar.");

        ButtonType changeButtonType = new ButtonType("Cambiar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(changeButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        PasswordField currentField = new PasswordField();
        currentField.setPromptText("Contraseña actual");
        PasswordField newField = new PasswordField();
        newField.setPromptText("Contraseña nueva");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirmar contraseña nueva");

        grid.add(new Label("Contraseña actual:"), 0, 0);
        grid.add(currentField, 1, 0);
        grid.add(new Label("Contraseña nueva:"), 0, 1);
        grid.add(newField, 1, 1);
        grid.add(new Label("Confirmar:"), 0, 2);
        grid.add(confirmField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == changeButtonType) {
                return new PasswordChangeInput(
                        currentField.getText(), newField.getText(), confirmField.getText());
            }
            return null;
        });

        return dialog.showAndWait();
    }

    /**
     * Applies the forced password change flow for a freshly logged-in user.
     * <p>
     * Returns a result that indicates whether the app should proceed to the main
     * view. When the change is cancelled, invalid or fails, the session is
     * logged out and the result says not to proceed.
     */
    static PasswordChangeResult evaluatePasswordChange(
            LoginPresenter presenter, User user, PasswordChangeInput input) {
        if (!user.isMustChangePassword()) {
            return PasswordChangeResult.success();
        }
        if (input == null) {
            presenter.logout();
            return PasswordChangeResult.cancelled();
        }
        String validationError = validatePasswordChange(
                input.currentPassword(), input.newPassword(), input.confirm());
        if (validationError != null) {
            presenter.logout();
            return PasswordChangeResult.failed(validationError);
        }
        if (!presenter.currentPasswordMatches(user, input.currentPassword())) {
            presenter.logout();
            return PasswordChangeResult.failed("La contraseña actual es incorrecta.");
        }
        try {
            presenter.changePassword(user.getId(), input.newPassword());
            return PasswordChangeResult.success();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "No se pudo cambiar la contraseña", e);
            presenter.logout();
            return PasswordChangeResult.failed("No se pudo cambiar la contraseña.");
        }
    }

    static String validatePasswordChange(String currentPassword, String newPassword, String confirm) {
        if (currentPassword == null || currentPassword.isEmpty()) {
            return "Ingrese su contraseña actual.";
        }
        if (newPassword == null || newPassword.isEmpty()) {
            return "Ingrese una contraseña nueva.";
        }
        if (confirm == null || confirm.isEmpty()) {
            return "Confirme la contraseña nueva.";
        }
        if (!newPassword.equals(confirm)) {
            return "Las contraseñas no coinciden.";
        }
        if (newPassword.equals(currentPassword)) {
            return "La contraseña nueva debe ser distinta a la actual.";
        }
        return null;
    }

    /**
     * Renders the login footer version label text. Never crashes on unknown
     * version metadata because {@code VersionInfo} falls back to "unknown".
     */
    static String versionFooterText(String version) {
        return "Versión " + version;
    }

    static String validateCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return "Ingrese un nombre de usuario.";
        }
        if (password == null || password.isEmpty()) {
            return "Ingrese una contraseña.";
        }
        return null;
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    /**
     * Values captured from the forced password change dialog.
     */
    record PasswordChangeInput(String currentPassword, String newPassword, String confirm) {
    }

    /**
     * Outcome of the forced password change flow. {@code proceed} tells the login
     * flow whether to navigate to the main view; {@code errorMessage} (when set)
     * is the reason the change was rejected.
     */
    record PasswordChangeResult(boolean proceed, String errorMessage) {

        static PasswordChangeResult success() {
            return new PasswordChangeResult(true, null);
        }

        static PasswordChangeResult cancelled() {
            return new PasswordChangeResult(false, null);
        }

        static PasswordChangeResult failed(String errorMessage) {
            return new PasswordChangeResult(false, errorMessage);
        }
    }

    /**
     * Values captured from the password recovery dialog.
     */
    record RecoveryInput(Long userId, String masterKey, String newPassword, String confirm) {
    }

    /**
     * Outcome of the password recovery flow. {@code wrongKey} is set when the
     * provider master key did not match, so callers can apply an anti
     * brute-force delay before the next attempt.
     */
    record RecoveryResult(boolean success, boolean wrongKey, String errorMessage) {

        static RecoveryResult successResult() {
            return new RecoveryResult(true, false, null);
        }

        static RecoveryResult notConfigured() {
            return new RecoveryResult(false, false,
                    "La recuperación no está configurada. Contacte al proveedor.");
        }

        static RecoveryResult wrongKeyResult() {
            return new RecoveryResult(false, true, "Clave de recuperación incorrecta.");
        }

        static RecoveryResult failed(String errorMessage) {
            return new RecoveryResult(false, false, errorMessage);
        }
    }
}
