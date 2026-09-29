package com.softwaredebebidas.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Threat-matrix RED tests (SDD change fix-audit-findings): path traversal
 * guard on {@link BackupService#exportBackup(Path)}. A crafted destination —
 * a relative path with ".." segments, or an absolute path containing ".."
 * segments — must be rejected with {@link IOException} before any file copy
 * is attempted.
 */
class BackupPathTraversalTest {

    private Path tempDir;
    private BackupService backupService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("softwaredebebidas-traversal-");
        Path dbFile = tempDir.resolve("test.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO test VALUES (1, 'hello')");
        }
        backupService = new BackupService(dbFile, tempDir.resolve("backups"));
        backupService.createBackup();
    }

    @AfterEach
    void tearDown() throws Exception {
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
    void rejectsRelativeTraversalPath() {
        Path attack = Path.of("..", "..", "..", "etc", "passwd");

        assertThatThrownBy(() -> backupService.exportBackup(attack))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void rejectsAbsolutePathContainingDotDotSegments() {
        Path attack = Path.of(System.getProperty("java.io.tmpdir"), "..", "..", "windows", "system32");

        assertThatThrownBy(() -> backupService.exportBackup(attack))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("..");
    }

    @Test
    void acceptsLegitimateAbsoluteDirectory() throws Exception {
        Path legit = Files.createDirectories(tempDir.resolve("legit-dest"));

        Path exported = backupService.exportBackup(legit);

        assertThat(exported).isNotNull();
        assertThat(exported).exists();
        assertThat(exported.getParent()).isEqualTo(legit);
    }
}
