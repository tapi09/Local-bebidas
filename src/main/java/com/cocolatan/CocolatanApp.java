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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main JavaFX Application entry point for Cocolatán / Central de Bebidas.
 * Initializes the database, seeds default admin, shows login, then main view.
 */
public class CocolatanApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(CocolatanApp.class.getName());

    /** Bounds how long the crash-path shutdown hook may spend exporting a backup. */
    static final long SHUTDOWN_EXPORT_TIMEOUT_SECONDS = 5;

    /**
     * Set when {@link #stop()} already handled the backup export decision on a
     * normal close. The JVM shutdown hook then skips the crash-path export so
     * cancelling the folder picker on normal close does not trigger a copy.
     */
    private static final AtomicBoolean exportHandledOnNormalClose = new AtomicBoolean(false);

    private static DatabaseManager databaseManager;
    private static BackupService backupService;
    private static Path exportDir;
    private static Stage primaryStage;
    private static String businessName = "Cocolatán";
    private BackupScheduler backupScheduler;

    /**
     * Holds the single-instance lock for the lifetime of the app. The OS
     * releases it automatically if the process dies, so a stale lock can
     * never block a later launch. Both fields must stay referenced while the
     * app runs; closing the channel would release the lock prematurely.
     */
    private FileChannel instanceLockChannel;
    private FileLock instanceLock;

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

        // Single-instance guard: only the first instance may run. A second
        // launch finds the lock taken, explains why it exits, and stops
        // before touching the database (concurrent SQLite writers can corrupt
        // data or cause "database is locked" errors).
        if (!acquireInstanceLock(appDataDir)) {
            AlertService.showErrorDialog(
                    "Cocolatán ya está abierto",
                    "El programa ya se encuentra en ejecución. Cierre la ventana "
                            + "abierta y vuelva a intentarlo."
            );
            return;
        }

        // Seed the bundled logo into the data directory on first run, so the
        // installed app reads the logo from where the current one lives and
        // replacing that file updates it in-place.
        LogoUtils.ensureLogo();

        installWindowIcon(stage);

        LoggingConfig.init(logDir.toString());

        // Install global error handler. The default handler only covers
        // background threads: the JavaFX runtime installs its own handler on
        // the FX Application Thread, so we must ALSO set the handler on the
        // current thread (which runs start()) to catch uncaught exceptions
        // raised by controller event handlers without killing the app.
        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (thread, throwable) -> {
            LOGGER.log(Level.SEVERE, "Uncaught exception in thread: " + thread.getName(), throwable);
            javafx.application.Platform.runLater(() ->
                    AlertService.showErrorDialog(
                            "Error inesperado",
                            "Ocurrió un error inesperado. Consulte el archivo de logs para más detalles."
                    )
            );
        };
        Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
        Thread.currentThread().setUncaughtExceptionHandler(uncaughtExceptionHandler);

        try {
            // Initialize database
            databaseManager = DatabaseManager.createFromFile(dbPath.toString());

            // Safety net: close DB on JVM shutdown (covers System.exit and crash paths).
            // The crash-path backup export runs first (headless, bounded timeout)
            // so the last-known export directory can still be read from the DB.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (shouldExportOnShutdown() && backupService != null && databaseManager != null) {
                    tryExportOnShutdown(
                            backupService,
                            new ConfigRepository(databaseManager),
                            SHUTDOWN_EXPORT_TIMEOUT_SECONDS
                    );
                }
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
     * Attempts to take an exclusive lock on {@code <appDataDir>/cocolatan.lock}.
     * <p>
     * A {@link FileLock} is held for as long as this object keeps its channel
     * open. The OS drops the lock automatically when the process exits (even
     * after a crash), so no stale lock file can block a later launch. {@code
     * tryLock} never blocks: it returns {@code null} when another process owns
     * the lock, which means Cocolatán is already running.
     *
     * @return true when this instance owns the lock and may proceed
     */
    private boolean acquireInstanceLock(Path appDataDir) {
        try {
            Path lockPath = appDataDir.resolve("cocolatan.lock");
            instanceLockChannel = FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            );
            instanceLock = instanceLockChannel.tryLock();
            if (instanceLock == null) {
                LOGGER.warning("Another Cocolatán instance already holds the lock; refusing to start");
                instanceLockChannel.close();
                instanceLockChannel = null;
                return false;
            }
            return true;
        } catch (IOException e) {
            // Failing to lock should not prevent the app from running; log and
            // proceed. The lock is a safety net, not a hard requirement.
            LOGGER.log(Level.WARNING, "Could not acquire single-instance lock", e);
            instanceLockChannel = null;
            instanceLock = null;
            return true;
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
        MainPresenter mainPresenter = MainPresenter.getInstance();
        if (mainPresenter != null) {
            mainPresenter.dispose();
        }
        if (backupScheduler != null) {
            backupScheduler.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        releaseInstanceLock();
    }

    /**
     * Releases the single-instance lock. Safe to call even when the lock was
     * never acquired or was released by the OS.
     */
    private void releaseInstanceLock() {
        if (instanceLock != null) {
            try {
                instanceLock.release();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to release instance lock", e);
            }
            instanceLock = null;
        }
        if (instanceLockChannel != null) {
            try {
                instanceLockChannel.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to close instance lock channel", e);
            }
            instanceLockChannel = null;
        }
    }

    // ──────────────────────────────────────────────
    // Backup export helpers (normal close + crash path)
    // ──────────────────────────────────────────────

    /**
     * Returns the last directory used for backup export, if one was persisted.
     * A missing/unreadable config value yields an empty result — never throws.
     */
    static Optional<Path> lastKnownExportDir(ConfigRepository config) {
        try {
            return config.getBackupExportDir()
                    .filter(s -> !s.isBlank())
                    .map(Path::of);
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Could not read last export directory", e);
            return Optional.empty();
        }
    }

    /**
     * Persists the given directory as the last backup export directory. A
     * persistence failure is logged, never thrown.
     */
    static void persistExportDir(ConfigRepository config, Path dir) {
        try {
            config.setBackupExportDir(dir.toString());
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Could not persist last export directory", e);
        }
    }

    /**
     * Best-effort headless backup export for the JVM shutdown hook (crash or
     * forced termination). Copies the latest internal backup to the
     * last-known export directory, bounded by {@code timeoutSeconds}. Runs no
     * UI, swallows every exception, and logs failures at WARN level only.
     */
    public static void tryExportOnShutdown(BackupService service, ConfigRepository config, long timeoutSeconds) {
        try {
            Optional<Path> lastDir = lastKnownExportDir(config);
            if (lastDir.isEmpty()) {
                return;
            }
            Path dest = lastDir.get();
            ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            });
            try {
                Future<Path> future = executor.submit(() -> service.exportBackup(dest));
                Path exported = future.get(timeoutSeconds, TimeUnit.SECONDS);
                if (exported != null) {
                    LOGGER.info("Crash-path backup exported to " + exported);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Crash-path backup export failed", e);
            } finally {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Crash-path backup export failed", e);
        }
    }

    /** Records that the normal-close path already handled the export decision. */
    public static void markExportHandledOnNormalClose() {
        exportHandledOnNormalClose.set(true);
    }

    /** True when the JVM shutdown hook should attempt the crash-path export. */
    public static boolean shouldExportOnShutdown() {
        return !exportHandledOnNormalClose.get();
    }

    /** Test hook: resets the normal-close export flag. */
    public static void resetExportHandledFlag() {
        exportHandledOnNormalClose.set(false);
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
