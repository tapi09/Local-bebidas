package com.cocolatan.service;

import com.cocolatan.CocolatanApp;
import com.cocolatan.repository.ConfigRepository;
import com.cocolatan.repository.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the crash-time (JVM shutdown hook) backup export path. The hook
 * must run headless (no JavaFX toolkit is started here), never throw, never
 * block JVM termination beyond a bounded timeout, and log failures at WARN.
 */
class BackupServiceCrashPathTest {

    private Path tempDir;
    private DatabaseManager dbManager;
    private ConfigRepository config;
    private BackupService backupService;
    private List<LogRecord> capturedRecords;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("cocolatan-crash-");
        Path dbFile = tempDir.resolve("test.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO test VALUES (1, 'hello')");
        }

        dbManager = DatabaseManager.createInMemory();
        config = new ConfigRepository(dbManager);
        backupService = new BackupService(dbFile, tempDir.resolve("backups"));
        backupService.createBackup();

        // Capture log records emitted by CocolatanApp's shutdown export path.
        capturedRecords = new ArrayList<>();
        Logger.getLogger(CocolatanApp.class.getName()).addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                capturedRecords.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
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

    @Test
    void crashPathExportsLatestBackupToLastKnownDirectory() throws Exception {
        Path lastDir = Files.createDirectories(tempDir.resolve("last-known"));
        config.setBackupExportDir(lastDir.toString());

        CocolatanApp.tryExportOnShutdown(backupService, config, 5);

        try (var files = Files.list(lastDir)) {
            Path exported = files.findFirst().orElseThrow(() -> new AssertionError("no export produced"));
            assertThat(exported.getFileName().toString()).startsWith("cocolatan_backup_");
        }
    }

    @Test
    void crashPathSkipsExportWithoutLastKnownDirectory() throws Exception {
        Path lastDir = Files.createDirectories(tempDir.resolve("never-used"));

        CocolatanApp.tryExportOnShutdown(backupService, config, 5);

        assertThat(lastDir).isEmptyDirectory();
    }

    @Test
    void crashPathLogsWarningAndContinuesWhenDestinationInvalid() throws Exception {
        Path notADirectory = Files.createFile(tempDir.resolve("invalid-dest"));
        config.setBackupExportDir(notADirectory.toString());

        CocolatanApp.tryExportOnShutdown(backupService, config, 5); // must not throw

        assertThat(capturedRecords)
                .anyMatch(r -> r.getLevel() == Level.WARNING && r.getMessage().contains("Crash-path backup export failed"));
    }

    @Test
    void crashPathIsBoundedByConfiguredTimeout() throws Exception {
        BackupService blocking = mock(BackupService.class);
        when(blocking.exportBackup(any())).thenAnswer(inv -> {
            Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            return null;
        });
        config.setBackupExportDir(tempDir.resolve("last-known").toString());

        long startNanos = System.nanoTime();
        CocolatanApp.tryExportOnShutdown(blocking, config, 1); // 1s timeout
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);

        assertThat(elapsedSeconds).isLessThan(5);
        assertThat(capturedRecords)
                .anyMatch(r -> r.getLevel() == Level.WARNING && r.getMessage().contains("Crash-path backup export failed"));
    }

    @Test
    void crashPathIsSkippedWhenNormalCloseAlreadyHandledExport() {
        CocolatanApp.resetExportHandledFlag();
        assertThat(CocolatanApp.shouldExportOnShutdown()).isTrue();

        CocolatanApp.markExportHandledOnNormalClose();

        assertThat(CocolatanApp.shouldExportOnShutdown()).isFalse();
        CocolatanApp.resetExportHandledFlag();
    }
}
