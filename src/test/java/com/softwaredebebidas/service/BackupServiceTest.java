package com.softwaredebebidas.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupServiceTest {

    private Path tempDir;
    private Path dbFile;
    private Connection dbConnection;
    private BackupService backupService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("softwaredebebidas-test-");
        dbFile = tempDir.resolve("test.db");
        dbConnection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        try (Statement stmt = dbConnection.createStatement()) {
            // Mirror production: WAL journal mode (DatabaseManager.configureConnection)
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO test VALUES (1, 'hello')");
        }
        // BackupService no longer receives the shared connection — it opens its own.
        backupService = new BackupService(dbFile, tempDir.resolve("backups"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dbConnection != null && !dbConnection.isClosed()) {
            dbConnection.close();
        }
        try (var files = Files.walk(tempDir)) {
            files.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                        }
                    });
        }
    }

    @Test
    void createBackupCreatesDbFile() throws Exception {
        Path backup = backupService.createBackup();
        assertThat(backup).exists();
        assertThat(backup.toString()).endsWith(".db");
    }

    @Test
    void createBackupProducesReadableBackupWithCommittedData() throws Exception {
        Path backup = backupService.createBackup();

        try (Connection backupConn = DriverManager.getConnection("jdbc:sqlite:" + backup);
             Statement stmt = backupConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM test WHERE id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("hello");
        }
    }

    @Test
    void createBackupDoesNotUseTheSharedConnection() throws Exception {
        dbConnection.close();

        Path backup = backupService.createBackup();

        assertThat(backup).exists();
    }

    @Test
    void createBackupDoesNotLockTheSharedConnection() throws Exception {
        backupService.createBackup();

        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate("INSERT INTO test VALUES (2, 'world')");
        }
        try (Statement stmt = dbConnection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void backupSucceedsWhileWriteTransactionIsInProgress() throws Exception {
        dbConnection.setAutoCommit(false);
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate("INSERT INTO test VALUES (3, 'pending-sale')");
        }

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<Path> backupFuture =
                    executor.submit(() -> backupService.createBackup());

            Path backup = backupFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);

            dbConnection.commit();

            assertThat(backup).exists();
            try (Connection backupConn = DriverManager.getConnection("jdbc:sqlite:" + backup);
                 Statement stmt = backupConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
            dbConnection.rollback();
        }
    }

    @Test
    void listBackupsReturnsExisting() throws Exception {
        backupService.createBackup();
        List<Path> backups = backupService.listBackups();
        assertThat(backups).hasSize(1);
    }

    @Test
    void cleanOldBackupsRemovesExcess() throws Exception {
        backupService.createBackup();
        Thread.sleep(1000);
        backupService.createBackup();

        backupService.cleanOldBackups(1);
        assertThat(backupService.listBackups()).hasSize(1);
    }

    // ──────────────────────────────────────────────
    // exportBackup(Path destDir)
    // ──────────────────────────────────────────────

    @Test
    void exportBackupCopiesLatestBackupToDestination() throws Exception {
        backupService.createBackup();
        Path destDir = Files.createDirectories(tempDir.resolve("export-dest"));

        Path exported = backupService.exportBackup(destDir);

        assertThat(exported).isNotNull();
        assertThat(exported.getParent()).isEqualTo(destDir);
        assertThat(exported).exists();
        assertThat(exported.getFileName().toString())
                .startsWith("softwaredebebidas_backup_")
                .endsWith(".db");
        // The copy is a readable SQLite database containing the source data.
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + exported);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM test WHERE id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("hello");
        }
    }

    @Test
    void exportBackupReturnsNullWhenNoBackupsExist() throws Exception {
        Path destDir = Files.createDirectories(tempDir.resolve("export-dest"));

        Path exported = backupService.exportBackup(destDir);

        assertThat(exported).isNull();
        assertThat(destDir).isEmptyDirectory();
    }

    @Test
    void exportBackupAddsSuffixWhenDestinationNameCollides() throws Exception {
        backupService.createBackup();
        Path destDir = Files.createDirectories(tempDir.resolve("export-dest"));

        Path first = backupService.exportBackup(destDir);
        Path second = backupService.exportBackup(destDir);

        assertThat(second).isNotEqualTo(first);
        assertThat(second.getFileName().toString()).matches(".*_\\d+\\.db$");
        // Original file untouched, second is a full independent copy.
        assertThat(first).exists();
        assertThat(second).exists();
        assertThat(Files.size(second)).isEqualTo(Files.size(first));
    }

    @Test
    void exportBackupRejectsMissingDestinationDirectory() throws Exception {
        backupService.createBackup();
        Path missing = tempDir.resolve("export-missing");

        assertThatThrownBy(() -> backupService.exportBackup(missing))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void exportBackupRejectsFileAsDestination() throws Exception {
        backupService.createBackup();
        Path file = Files.createFile(tempDir.resolve("not-a-directory"));

        assertThatThrownBy(() -> backupService.exportBackup(file))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a directory");
    }
}
