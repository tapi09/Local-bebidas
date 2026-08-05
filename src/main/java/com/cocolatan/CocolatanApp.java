package com.cocolatan;

import com.cocolatan.model.User;
import com.cocolatan.presenter.MainPresenter;
import com.cocolatan.repository.ConfigRepository;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.UserRepository;
import com.cocolatan.service.AuthService;
import com.cocolatan.service.BackupScheduler;
import com.cocolatan.service.BackupService;
import com.cocolatan.util.AlertService;
import com.cocolatan.util.LoggingConfig;
import com.cocolatan.util.LogoUtils;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main JavaFX Application entry point for Cocolatán / Central de Bebidas.
 * Initializes the database, seeds default admin, shows login, then main view.
 */
public class CocolatanApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(CocolatanApp.class.getName());
    private static DatabaseManager databaseManager;
    private static BackupService backupService;
    private static Path exportDir;
    private static Stage primaryStage;
    private static String businessName = "Cocolatán";
    private BackupScheduler backupScheduler;

    @Override
    public void start(Stage stage) {
        CocolatanApp.primaryStage = stage;

        // Resolve data directories under %APPDATA%/Cocolatan/
        Path appDataDir = Path.of(System.getenv("APPDATA"), "Cocolatan");
        Path dbPath = appDataDir.resolve("cocolatan.db");
        Path logDir = appDataDir.resolve("logs");
        Path exportDirPath = appDataDir.resolve("exports");
        Path backupDir = appDataDir.resolve("backups");

        try {
            Files.createDirectories(appDataDir);
            Files.createDirectories(logDir);
            Files.createDirectories(exportDirPath);
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create data directories", e);
            AlertService.showErrorDialog(
                    "Error",
                    "No se pudieron crear los directorios de datos en " + appDataDir
            );
            return;
        }

        // Seed the bundled logo into the data directory on first run, so the
        // installed app reads the logo from where the current one lives and
        // replacing that file updates it in-place.
        LogoUtils.ensureLogo();

        installWindowIcon(stage);

        LoggingConfig.init(logDir.toString());

        // Install global error handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.log(Level.SEVERE, "Uncaught exception in thread: " + thread.getName(), throwable);
            javafx.application.Platform.runLater(() ->
                    AlertService.showErrorDialog(
                            "Error inesperado",
                            "Ocurrió un error inesperado. Consulte el archivo de logs para más detalles."
                    )
            );
        });

        try {
            // Initialize database
            databaseManager = DatabaseManager.createFromFile(dbPath.toString());

            // Safety net: close DB on JVM shutdown (covers System.exit and crash paths)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (backupScheduler != null) {
                    backupScheduler.stop();
                }
                if (databaseManager != null) {
                    databaseManager.close();
                }
            }));

            // Initialize export directory
            exportDir = exportDirPath;

            // Initialize automatic backups every 30 minutes, keep last 10.
            // BackupService opens its own SQLite connection for VACUUM INTO so the
            // backup never competes for the single shared DatabaseManager connection.
            backupService = new BackupService(dbPath, backupDir);
            backupScheduler = new BackupScheduler(backupService);
            backupScheduler.start(30, 10);

            // Seed default admin user if not exists
            seedDefaultAdmin();

            // Load business name from config
            loadBusinessName();

            // Initialize AuthService
            AuthService.initialize(new UserRepository(databaseManager), new ConfigRepository(databaseManager));

            // Show login view first
            showLoginView();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database", e);
            AlertService.showErrorDialog("Error", "No se pudo inicializar la base de datos.");
        }
    }

    /**
     * Sets the window icon preferring the data-directory logo, falling back to
     * the classpath brand tile. Never crashes when neither is available.
     */
    private static void installWindowIcon(Stage stage) {
        try {
            Path logoPath = LogoUtils.resolveLogoPath();
            if (logoPath != null && Files.isReadable(logoPath)) {
                Image image = new Image(logoPath.toUri().toString());
                if (!image.isError()) {
                    stage.getIcons().add(image);
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load window icon from data directory", e);
        }
        try (InputStream iconStream = CocolatanApp.class.getResourceAsStream("/icons/cocolatan.png")) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load window icon", e);
        }
    }

    private void seedDefaultAdmin() {
        try {
            UserRepository userRepo = new UserRepository(databaseManager);
            if (userRepo.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(AuthService.hashPassword("admin123"));
                admin.setRole("ADMIN");
                admin.setDisplayName("Administrador");
                admin.setMustChangePassword(true);
                userRepo.save(admin);
                LOGGER.info("Default admin user created");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to seed default admin", e);
        }
    }

    private static void loadBusinessName() {
        try {
            ConfigRepository configRepo = new ConfigRepository(databaseManager);
            businessName = configRepo.get("business_name").orElse("Cocolatán");
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load business name, using default", e);
        }
    }

    public static void showLoginView() {
        try {
            FXMLLoader loader = new FXMLLoader(CocolatanApp.class.getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 380, 350);
            scene.getStylesheets().add("/styles.css");
            primaryStage.setTitle(businessName + " - Iniciar Sesión");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load login view", e);
            AlertService.showErrorDialog("Error", "No se pudo cargar la pantalla de inicio de sesión.");
        }
    }

    public static void showMainView() {
        try {
            FXMLLoader loader = new FXMLLoader(CocolatanApp.class.getResource("/fxml/main.fxml"));
            Parent root = loader.load();
            MainPresenter mainPresenter = loader.getController();

            Scene scene = new Scene(root, 1100, 680);
            scene.getStylesheets().add("/styles.css");
            primaryStage.setTitle(businessName + " - Central de Bebidas");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(540);
            primaryStage.setMaxWidth(Double.MAX_VALUE);
            primaryStage.setMaxHeight(Double.MAX_VALUE);
            primaryStage.setMaximized(true);

            // Wire keyboard shortcuts after scene is ready
            mainPresenter.setupKeyboardShortcuts(scene);

            primaryStage.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load main view", e);
            AlertService.showErrorDialog("Error", "No se pudo cargar la interfaz principal.");
        }
    }

    @Override
    public void stop() {
        if (backupScheduler != null) {
            backupScheduler.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    /**
     * Returns the database manager instance.
     */
    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Returns the export directory path.
     */
    public static Path getExportDir() {
        return exportDir;
    }

    public static BackupService getBackupService() {
        return backupService;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
