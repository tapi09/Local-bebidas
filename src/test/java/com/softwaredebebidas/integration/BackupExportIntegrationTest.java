package com.softwaredebebidas.integration;

import com.softwaredebebidas.SoftwareDeBebidasApp;
import com.softwaredebebidas.presenter.MainPresenter;
import com.softwaredebebidas.repository.ConfigRepository;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.service.BackupService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the backup export flows (SDD change fix-audit-findings).
 *
 * <p>Deviation note: the design proposed TestFX UI automation, but this repo
 * has no TestFX harness (no base class, no headless glass config), so the
 * flows are exercised with real components (real SQLite DB via
 * {@link DatabaseManager}, real {@link BackupService} on temp dirs, real
 * {@link ConfigRepository}) and the folder-picker decision injected as a
 * {@code Supplier<Path>} — matching the repository's existing integration
 * style (in-memory DB + real services). The directory-chooser dialog itself is
 * a thin native wrapper that cannot run headless.</p>
 */
class BackupExportIntegrationTest {

    private Path tempDir;
    private Path dbFile;
    private DatabaseManager dbManager;
    private ConfigRepository config;
    private BackupService backupService;

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (Exception e) {
            // already started or headless
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("softwaredebebidas-integration-");
        dbFile = tempDir.resolve("test.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO test VALUES (1, 'hello')");
        }
        dbManager = DatabaseManager.createInMemory();
        config = new ConfigRepository(dbManager);
        backupService = new BackupService(dbFile, tempDir.resolve("backups"));
        backupService.createBackup();
    }

    @AfterEach
    void tearDown() throws Exception {
        dbManager.close();
        try (var files = Files.walk(tempDir)) {
            files.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // best-effort cleanup
                        }
                    });
        }
    }

    // ──────────────────────────────────────────────
    // Normal close flow (Application.stop)
    // ──────────────────────────────────────────────

    @Test
    void normalCloseWithChosenDirectoryCopiesBackupAndRemembersDirectory() throws Exception {
        Path chosen = Files.createDirectories(tempDir.resolve("user-chosen"));

        Path dest = SoftwareDeBebidasApp.resolveExportDestination(config, () -> chosen);
        Path exported = backupService.exportBackup(dest);

        assertThat(dest).isEqualTo(chosen);
        assertThat(exported).exists();
        assertThat(exported.getParent()).isEqualTo(chosen);
        // Directory remembered for next export / crash fallback.
        assertThat(config.getBackupExportDir()).contains(chosen.toString());
    }

    @Test
    void normalCloseWithCancelledChooserDoesNotCopyOrPersist() throws Exception {
        Path dest = SoftwareDeBebidasApp.resolveExportDestination(config, () -> null);

        assertThat(dest).isNull();
        assertThat(config.getBackupExportDir()).isEmpty();
    }

    @Test
    void normalCloseWithFailingChooserFallsBackToLastKnownDirectory() throws Exception {
        Path lastKnown = Files.createDirectories(tempDir.resolve("last-known"));
        config.setBackupExportDir(lastKnown.toString());

        Path dest = SoftwareDeBebidasApp.resolveExportDestination(config, () -> {
            throw new IllegalStateException("dialog unavailable during stop()");
        });

        assertThat(dest).isEqualTo(lastKnown);
        Path exported = backupService.exportBackup(dest);
        assertThat(exported).exists();
        assertThat(exported.getParent()).isEqualTo(lastKnown);
    }

    @Test
    void normalCloseWithChosenDirectoryOverwritesRememberedDirectory() throws Exception {
        Path previous = Files.createDirectories(tempDir.resolve("previous"));
        config.setBackupExportDir(previous.toString());
        Path chosen = Files.createDirectories(tempDir.resolve("new-chosen"));

        SoftwareDeBebidasApp.resolveExportDestination(config, () -> chosen);

        assertThat(config.getBackupExportDir()).contains(chosen.toString());
        assertThat(config.getBackupExportDir().get()).isNotEqualTo(previous.toString());
    }

    // ──────────────────────────────────────────────
    // Crash path (JVM shutdown hook)
    // ──────────────────────────────────────────────

    @Test
    void crashSimulationExportsHeadlesslyToLastKnownDirectory() throws Exception {
        Path lastKnown = Files.createDirectories(tempDir.resolve("crash-dest"));
        config.setBackupExportDir(lastKnown.toString());

        // Exactly what the registered shutdown hook invokes (before DB close).
        SoftwareDeBebidasApp.tryExportOnShutdown(backupService, config, 5);

        try (var files = Files.list(lastKnown)) {
            Optional<Path> exported = files.findFirst();
            assertThat(exported).isPresent();
            assertThat(exported.get().getFileName().toString()).startsWith("softwaredebebidas_backup_");
        }
    }

    // ──────────────────────────────────────────────
    // Manual "Export Backup" button (MainPresenter)
    // ──────────────────────────────────────────────

    @Test
    void manualExportButtonCopiesBackupShowsToastAndRemembersDirectory() throws Exception {
        Path chosen = Files.createDirectories(tempDir.resolve("manual-chosen"));
        MainPresenter presenter = newMainPresenter();

        withMockedDirectoryChooserAndService(presenter, chosen, () -> presenter.onExportBackup());

        // Backup copied to the chosen directory.
        try (var files = Files.list(chosen)) {
            Optional<Path> exported = files.findFirst();
            assertThat(exported).isPresent();
            assertThat(exported.get().getFileName().toString()).startsWith("softwaredebebidas_backup_");
        }
        // Success toast shown in the content area.
        assertThat(presenterContentArea(presenter)).hasSize(1);
        assertThat(((Label) presenterContentArea(presenter).get(0)).getText())
                .startsWith("Respaldo exportado a ");
        // Directory remembered for next export / crash fallback.
        assertThat(config.getBackupExportDir()).contains(chosen.toString());
    }

    @Test
    void manualExportButtonWithCancelledChooserDoesNothing() throws Exception {
        Path chosen = Files.createDirectories(tempDir.resolve("manual-cancelled"));
        MainPresenter presenter = newMainPresenter();

        withMockedDirectoryChooserAndService(presenter, null, () -> presenter.onExportBackup());

        assertThat(chosen).isEmptyDirectory();
        assertThat(presenterContentArea(presenter)).isEmpty();
        assertThat(config.getBackupExportDir()).isEmpty();
    }

    @Test
    void manualExportButtonWithFailedCopyShowsErrorToast() throws Exception {
        Path notADirectory = Files.createFile(tempDir.resolve("blocked-dest"));
        MainPresenter presenter = newMainPresenter();

        withMockedDirectoryChooserAndService(presenter, notADirectory, () -> presenter.onExportBackup());

        assertThat(presenterContentArea(presenter)).hasSize(1);
        assertThat(((Label) presenterContentArea(presenter).get(0)).getText())
                .startsWith("Error al exportar respaldo:");
    }

    /**
     * Runs {@code action} while {@code new DirectoryChooser()} returns a mock
     * whose {@code showDialog} returns the given directory (or null for
     * cancel), and {@code SoftwareDeBebidasApp.getBackupService()} returns the real
     * backup service.
     */
    private void withMockedDirectoryChooserAndService(MainPresenter presenter, Path chosenDir,
                                                      Runnable action) {
        try (MockedConstruction<DirectoryChooser> construction =
                     mockConstruction(DirectoryChooser.class, (mock, ctx) ->
                             when(mock.showDialog(any())).thenReturn(
                                     chosenDir != null ? chosenDir.toFile() : null));
             MockedStatic<SoftwareDeBebidasApp> app = mockStatic(SoftwareDeBebidasApp.class)) {
            app.when(SoftwareDeBebidasApp::getBackupService).thenReturn(backupService);
            // Real persistence: the config must actually be written, not no-op'd.
            app.when(() -> SoftwareDeBebidasApp.persistExportDir(any(ConfigRepository.class), any(Path.class)))
                    .thenCallRealMethod();
            action.run();
        }
    }

    private MainPresenter newMainPresenter() throws Exception {
        MainPresenter presenter = new MainPresenter();
        setField(presenter, "contentArea", new StackPane());
        setField(presenter, "configRepository", config);
        return presenter;
    }

    private javafx.collections.ObservableList<javafx.scene.Node> presenterContentArea(MainPresenter presenter) {
        try {
            java.lang.reflect.Field f = presenter.getClass().getDeclaredField("contentArea");
            f.setAccessible(true);
            return ((StackPane) f.get(presenter)).getChildren();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
