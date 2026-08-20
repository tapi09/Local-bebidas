package com.cocolatan.presenter;

import com.cocolatan.CocolatanApp;
import com.cocolatan.repository.ConfigRepository;
import com.cocolatan.service.AuthService;
import com.cocolatan.service.BackupService;
import com.cocolatan.ui.ToastService;
import com.cocolatan.util.AlertService;
import com.cocolatan.util.LogoUtils;
import com.cocolatan.util.Refreshable;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.scene.control.TextInputDialog;

/**
 * Presenter for the main view. Handles sidebar navigation and content swapping.
 * Shows or hides sidebar sections based on user role.
 */
public class MainPresenter {

    private static final Logger LOGGER = Logger.getLogger(MainPresenter.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static MainPresenter instance;

    // Background thread that refreshes the clock label every second. Kept as a
    // field so it can be interrupted on logout/app stop, preventing a new
    // thread + scene-graph leak on every login cycle.
    private Thread clockThread;

    // Set to false to stop the clock thread (logout, shutdown, detached label).
    private final java.util.concurrent.atomic.AtomicBoolean clockActive =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    @FXML
    private StackPane contentArea;

    @FXML
    private Label dateTimeLabel;

    @FXML
    private Label sidebarLogoLabel;

    @FXML
    private ImageView sidebarLogoImage;

    @FXML
    private Label topBarTitleLabel;

    @FXML
    private Button btnInicio;

    @FXML
    private Button btnProductos;

    @FXML
    private Button btnCompras;

    @FXML
    private Button btnVentas;

    @FXML
    private Button btnStock;

    @FXML
    private Button btnAlertas;

    @FXML
    private Button btnReportes;

    @FXML
    private Button btnProveedores;

    @FXML
    private Button btnCategorias;

    @FXML
    private Button btnUsuarios;

    @FXML
    private Button btnDarkMode;

    @FXML
    private Label userInfoLabel;

    private boolean isDarkMode = false;

    private String businessName = "Cocolatán";

    private final Map<String, ViewEntry> viewCache = new HashMap<>();
    private String activeButtonId;
    private ConfigRepository configRepository;

    private static class ViewEntry {
        final Node node;
        final Object controller;

        ViewEntry(Node node, Object controller) {
            this.node = node;
            this.controller = controller;
        }
    }

    @FXML
    public void initialize() {
        instance = this;
        updateDateTime();

        // Update clock every second
        clockThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && clockActive.get()) {
                try {
                    Thread.sleep(1000);
                    Platform.runLater(() -> {
                        // Stop the clock when the label is gone/detached.
                        if (dateTimeLabel == null) {
                            clockActive.set(false);
                            return;
                        }
                        updateDateTime();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        clockThread.setDaemon(true);
        clockThread.start();

        // Load and apply dark mode preference
        loadDarkModePreference();
        applyDarkMode();

        // Load business name from config
        loadBusinessName();

        // Show the brand logo when available, falling back to the name label
        loadLogo();

        // Configure sidebar visibility and user info based on role
        configureForRole();

        // Load home view by default
        onInicio();
    }

    void configureForRole() {
        AuthService auth = AuthService.getInstance();
        if (auth.getCurrentUser() != null) {
            String display = auth.getCurrentUser().getDisplayName();
            String roleLabel = auth.isAdmin() ? "Admin" : "Cajero";
            if (userInfoLabel != null) {
                userInfoLabel.setText(display + " (" + roleLabel + ")");
            }
        }

        if (auth.isCajero()) {
            if (btnProductos != null) {
                btnProductos.setVisible(false);
                btnProductos.setManaged(false);
            }
            if (btnCompras != null) {
                btnCompras.setVisible(false);
                btnCompras.setManaged(false);
            }
            if (btnProveedores != null) {
                btnProveedores.setVisible(false);
                btnProveedores.setManaged(false);
            }
            if (btnCategorias != null) {
                btnCategorias.setVisible(false);
                btnCategorias.setManaged(false);
            }
            if (btnUsuarios != null) {
                btnUsuarios.setVisible(false);
                btnUsuarios.setManaged(false);
            }
        }
    }

    /**
     * Sets up keyboard shortcuts for the main window.
     */
    public void setupKeyboardShortcuts(Scene scene) {
        // Direct KeyPress handler for F2..F8 and ESCAPE
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case F2 -> onVentas();
                case F3 -> onProductos();
                case F4 -> onStock();
                case F5 -> onCompras();
                case F6 -> onReportes();
                case F7 -> onAlertas();
                case F8 -> onProveedores();
                case ESCAPE -> onSalir();
                default -> {}
            }
        });

        // Ctrl+1..7 — navigate sidebar sections
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN),
                this::onInicio
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN),
                this::onProductos
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN),
                this::onCompras
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.CONTROL_DOWN),
                this::onVentas
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT5, KeyCombination.CONTROL_DOWN),
                this::onStock
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT6, KeyCombination.CONTROL_DOWN),
                this::onAlertas
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT7, KeyCombination.CONTROL_DOWN),
                this::onReportes
        );
    }

    private String getFxmlForButton(String buttonId) {
        return switch (buttonId) {
            case "btnInicio" -> "/fxml/home.fxml";
            case "btnProductos" -> "/fxml/product.fxml";
            case "btnCompras" -> "/fxml/purchase.fxml";
            case "btnVentas" -> "/fxml/sale.fxml";
            case "btnStock" -> "/fxml/stock.fxml";
            case "btnAlertas" -> "/fxml/alert.fxml";
            case "btnReportes" -> "/fxml/reports.fxml";
            case "btnProveedores" -> "/fxml/supplier.fxml";
            case "btnCategorias" -> "/fxml/categories.fxml";
            case "btnUsuarios" -> "/fxml/user.fxml";
            default -> null;
        };
    }

    private String getViewName(String buttonId) {
        return switch (buttonId) {
            case "btnInicio" -> "Inicio";
            case "btnProductos" -> "Productos";
            case "btnCompras" -> "Compras";
            case "btnVentas" -> "Ventas";
            case "btnStock" -> "Stock";
            case "btnAlertas" -> "Alertas";
            case "btnReportes" -> "Reportes";
            case "btnProveedores" -> "Proveedores";
            case "btnCategorias" -> "Categorías";
            case "btnUsuarios" -> "Usuarios";
            default -> "";
        };
    }

    private void updateDateTime() {
        dateTimeLabel.setText(LocalDateTime.now().format(TIME_FORMATTER));
    }

    /**
     * Returns the singleton MainPresenter instance (registered during initialize).
     */
    public static MainPresenter getInstance() {
        return instance;
    }

    @FXML
    public void onInicio() {
        setActiveButton("btnInicio");
        loadView("/fxml/home.fxml", "Inicio");
    }

    @FXML
    public void onProductos() {
        if (!checkAdminAccess()) return;
        setActiveButton("btnProductos");
        loadView("/fxml/product.fxml", "Productos");
    }

    @FXML
    public void onCompras() {
        if (!checkAdminAccess()) return;
        setActiveButton("btnCompras");
        loadView("/fxml/purchase.fxml", "Compras");
    }

    @FXML
    public void onVentas() {
        setActiveButton("btnVentas");
        loadView("/fxml/sale.fxml", "Ventas");
    }

    /**
     * Navigates to the Sale History view.
     */
    public void onSaleHistory() {
        loadView("/fxml/sale-history.fxml", "Historial de Ventas");
    }

    @FXML
    public void onStock() {
        setActiveButton("btnStock");
        loadView("/fxml/stock.fxml", "Stock");
    }

    @FXML
    public void onAlertas() {
        setActiveButton("btnAlertas");
        loadView("/fxml/alert.fxml", "Alertas");
    }

    @FXML
    public void onReportes() {
        setActiveButton("btnReportes");
        loadView("/fxml/reports.fxml", "Reportes");
    }

    @FXML
    public void onProveedores() {
        if (!checkAdminAccess()) return;
        setActiveButton("btnProveedores");
        loadView("/fxml/supplier.fxml", "Proveedores");
    }

    @FXML
    public void onCategorias() {
        if (!checkAdminAccess()) return;
        setActiveButton("btnCategorias");
        loadView("/fxml/categories.fxml", "Categorías");
    }

    @FXML
    public void onUsuarios() {
        if (!checkAdminAccess()) return;
        setActiveButton("btnUsuarios");
        loadView("/fxml/user.fxml", "Usuarios");
    }

    @FXML
    public void onSalir() {
        Platform.exit();
    }

    @FXML
    public void onLogout() {
        dispose();
        AuthService.getInstance().logout();
        viewCache.clear();
        CocolatanApp.showLoginView();
    }

    /**
     * Stops the background clock thread so it does not leak across login
     * cycles. Called on logout and from CocolatanApp.stop() during shutdown.
     */
    public void dispose() {
        clockActive.set(false);
        if (clockThread != null) {
            clockThread.interrupt();
            clockThread = null;
        }
    }

    /**
     * Manual "Export Backup" action (sidebar button). Opens a folder picker
     * every time, copies the latest internal backup to the selected directory,
     * remembers the directory for the next export and the crash fallback, and
     * shows a success/failure toast. Cancelling the picker does nothing.
     */
    @FXML
    public void onExportBackup() {
        try {
            BackupService backupService = CocolatanApp.getBackupService();
            if (backupService == null) {
                showToast("Error al exportar respaldo: servicio no disponible");
                return;
            }
            ConfigRepository config = configRepository();
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Exportar copia de seguridad");
            CocolatanApp.lastKnownExportDir(config)
                    .ifPresent(dir -> chooser.setInitialDirectory(dir.toFile()));

            Window owner = (contentArea != null && contentArea.getScene() != null)
                    ? contentArea.getScene().getWindow()
                    : null;
            java.io.File chosen = chooser.showDialog(owner);
            if (chosen == null) {
                return; // user cancelled the folder picker
            }
            Path dest = chosen.toPath();

            CocolatanApp.persistExportDir(config, dest);
            Path exported = backupService.exportBackup(dest);
            if (exported != null) {
                showToast("Respaldo exportado a " + dest);
            } else {
                showToast("No hay respaldos para exportar");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Manual backup export failed", e);
            showToast("Error al exportar respaldo: " + e.getMessage());
        }
    }

    /**
     * Shows a non-blocking toast over the content area (no-op when the view
     * is detached, e.g. during shutdown). Used for backup export feedback.
     */
    public void showToast(String message) {
        if (contentArea == null) {
            return;
        }
        ToastService.show(contentArea, message);
    }

    private boolean checkAdminAccess() {
        if (!AuthService.getInstance().isAdmin()) {
            AlertService.showWarningDialog("Acceso Denegado", "Acceso solo para administradores.");
            return false;
        }
        return true;
    }

    // ──────────────────────────────────────────────
    // Dark Mode Toggle
    // ──────────────────────────────────────────────

    @FXML
    private void onToggleDarkMode() {
        isDarkMode = !isDarkMode;
        applyDarkMode();
        saveDarkModePreference();
    }

    void loadDarkModePreference() {
        try {
            isDarkMode = "true".equals(configRepository().get("dark_mode").orElse("false"));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load dark mode preference", e);
        }
    }

    void saveDarkModePreference() {
        try {
            configRepository().set("dark_mode", String.valueOf(isDarkMode));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Could not save dark mode preference", e);
        }
    }

    // ──────────────────────────────────────────────
    // Business Name
    // ──────────────────────────────────────────────

    void loadBusinessName() {
        try {
            businessName = configRepository().get("business_name").orElse("Cocolatán");
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load business name", e);
        }
        applyBusinessName();
    }

    void applyBusinessName() {
        if (sidebarLogoLabel != null) {
            sidebarLogoLabel.setText(businessName);
        }
        if (topBarTitleLabel != null) {
            topBarTitleLabel.setText(businessName + " — Central de Bebidas");
        }
    }

    /**
     * Shows the brand logo image in the sidebar when it is available, hiding
     * the business-name label in that case. Keeps the label otherwise. Never
     * throws.
     */
    void loadLogo() {
        try {
            Path path = LogoUtils.resolveLogoPath();
            if (path != null && Files.isReadable(path) && sidebarLogoImage != null) {
                Image image = new Image(path.toUri().toString());
                if (!image.isError()) {
                    sidebarLogoImage.setImage(image);
                    sidebarLogoImage.setVisible(true);
                    sidebarLogoImage.setManaged(true);
                    if (sidebarLogoLabel != null) {
                        sidebarLogoLabel.setVisible(false);
                        sidebarLogoLabel.setManaged(false);
                    }
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load sidebar logo", e);
        }
        if (sidebarLogoImage != null) {
            sidebarLogoImage.setVisible(false);
            sidebarLogoImage.setManaged(false);
        }
    }

    @FXML
    void onEditBusinessName() {
        TextInputDialog dialog = new TextInputDialog(businessName);
        dialog.setTitle("Editar Nombre del Negocio");
        dialog.setHeaderText("Ingrese el nombre del negocio:");
        dialog.setContentText("Nombre:");
        dialog.showAndWait().ifPresent(newName -> {
            String trimmed = newName.trim();
            if (!trimmed.isEmpty() && !trimmed.equals(businessName)) {
                try {
                    configRepository().set("business_name", trimmed);
                    businessName = trimmed;
                    applyBusinessName();
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Could not save business name", e);
                }
            }
        });
    }

    void setConfigRepository(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    private ConfigRepository configRepository() {
        if (configRepository == null) {
            configRepository = new ConfigRepository(CocolatanApp.getDatabaseManager());
        }
        return configRepository;
    }

    private void applyDarkMode() {
        if (contentArea == null || contentArea.getScene() == null) {
            return;
        }
        Scene scene = contentArea.getScene();
        // Ensure base stylesheet is always at Scene level so both light and dark
        // files compete at the same cascade priority (Scene-level).
        if (!scene.getStylesheets().contains("/styles.css")) {
            scene.getStylesheets().add("/styles.css");
        }
        if (isDarkMode) {
            if (!scene.getStylesheets().contains("/styles-dark.css")) {
                scene.getStylesheets().add("/styles-dark.css");
            }
            if (btnDarkMode != null) {
                btnDarkMode.setText("Modo Claro");
            }
        } else {
            scene.getStylesheets().remove("/styles-dark.css");
            if (btnDarkMode != null) {
                btnDarkMode.setText("Modo Oscuro");
            }
        }
    }

    private void setActiveButton(String buttonId) {
        // Remove highlight from all buttons
        if (btnInicio != null) btnInicio.getStyleClass().remove("sidebar-button-active");
        if (btnProductos != null) btnProductos.getStyleClass().remove("sidebar-button-active");
        if (btnCompras != null) btnCompras.getStyleClass().remove("sidebar-button-active");
        if (btnVentas != null) btnVentas.getStyleClass().remove("sidebar-button-active");
        if (btnStock != null) btnStock.getStyleClass().remove("sidebar-button-active");
        if (btnAlertas != null) btnAlertas.getStyleClass().remove("sidebar-button-active");
        if (btnReportes != null) btnReportes.getStyleClass().remove("sidebar-button-active");
        if (btnProveedores != null) btnProveedores.getStyleClass().remove("sidebar-button-active");
        if (btnCategorias != null) btnCategorias.getStyleClass().remove("sidebar-button-active");
        if (btnUsuarios != null) btnUsuarios.getStyleClass().remove("sidebar-button-active");

        // Add highlight to selected button
        Button activeButton = getButtonById(buttonId);
        if (activeButton != null) {
            activeButton.getStyleClass().add("sidebar-button-active");
        }
        activeButtonId = buttonId;
    }

    private Button getButtonById(String id) {
        return switch (id) {
            case "btnInicio" -> btnInicio;
            case "btnProductos" -> btnProductos;
            case "btnCompras" -> btnCompras;
            case "btnVentas" -> btnVentas;
            case "btnStock" -> btnStock;
            case "btnAlertas" -> btnAlertas;
            case "btnReportes" -> btnReportes;
            case "btnProveedores" -> btnProveedores;
            case "btnCategorias" -> btnCategorias;
            case "btnUsuarios" -> btnUsuarios;
            default -> null;
        };
    }

    private void loadView(String fxmlPath, String viewName) {
        try {
            ViewEntry entry = viewCache.computeIfAbsent(fxmlPath, path -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
                    Node node = loader.load();
                    return new ViewEntry(node, loader.getController());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load view: " + path, e);
                    Label errorLabel = new Label("Vista no disponible: " + viewName);
                    errorLabel.getStyleClass().add("error-label");
                    return new ViewEntry(errorLabel, null);
                }
            });

            // Refresh data if the controller supports it
            if (entry.controller instanceof Refreshable) {
                ((Refreshable) entry.controller).refresh();
            }

            contentArea.getChildren().setAll(entry.node);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load view: " + fxmlPath, e);
            Label errorLabel = new Label("Vista no disponible: " + viewName);
            errorLabel.getStyleClass().add("error-label");
            contentArea.getChildren().setAll(errorLabel);
        }
    }
}
