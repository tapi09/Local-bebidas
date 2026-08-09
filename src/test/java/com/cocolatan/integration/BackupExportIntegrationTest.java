package com.cocolatan.integration;

import com.cocolatan.CocolatanApp;
import com.cocolatan.repository.ConfigRepository;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.service.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("cocolatan-integration-");
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

        Path dest = CocolatanApp.resolveExportDestination(config, () -> chosen);
        Path exported = backupService.exportBackup(dest);

        assertThat(dest).isEqualTo(chosen);
        assertThat(exported).exists();
        assertThat(exported.getParent()).isEqualTo(chosen);
        // Directory remembered for next export / crash fallback.
        assertThat(config.getBackupExportDir()).contains(chosen.toString());
    }

    @Test
    void normalCloseWithCancelledChooserDoesNotCopyOrPersist() throws Exception {
        Path dest = CocolatanApp.resolveExportDestination(config, () -> null);

        assertThat(dest).isNull();
        assertThat(config.getBackupExportDir()).isEmpty();
    }

    @Test
    void normalCloseWithFailingChooserFallsBackToLastKnownDirectory() throws Exception {
        Path lastKnown = Files.createDirectories(tempDir.resolve("last-known"));
        config.setBackupExportDir(lastKnown.toString());

        Path dest = CocolatanApp.resolveExportDestination(config, () -> {
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

        CocolatanApp.resolveExportDestination(config, () -> chosen);

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
        CocolatanApp.tryExportOnShutdown(backupService, config, 5);

        try (var files = Files.list(lastKnown)) {
            Optional<Path> exported = files.findFirst();
            assertThat(exported).isPresent();
            assertThat(exported.get().getFileName().toString()).startsWith("cocolatan_backup_");
        }
    }
}
