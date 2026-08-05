package com.cocolatan.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BackupService {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path dbPath;
    private final Path backupDir;

    /**
     * @param dbPath    path to the main database file
     * @param backupDir directory where backup files are stored
     */
    public BackupService(Path dbPath, Path backupDir) {
        this.dbPath = dbPath;
        this.backupDir = backupDir;
    }

    /**
     * Creates a full backup using {@code VACUUM INTO} on a SEPARATE SQLite
     * connection, never the shared DatabaseManager connection.
     *
     * <p>Concurrency decision (audit Fase 1, option A): the backup runs on the
     * backup thread while the UI writes through the single shared connection.
     * {@code VACUUM INTO} on a dedicated connection only takes a SHARED lock on
     * the source database, so it never competes for the shared connection and
     * does not block the UI writers (WAL mode). The connection is opened and
     * closed for the duration of the VACUUM and never leaks back to the app.</p>
     */
    public Path createBackup() throws IOException, SQLException {
        Files.createDirectories(backupDir);
        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        Path backupFile = backupDir.resolve("cocolatan_backup_" + timestamp + ".db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {
            // Decision (audit Fase 3): VACUUM INTO cannot use bind parameters — SQLite's
            // VACUUM grammar takes the destination as literal SQL text, not a bindable
            // value, so a PreparedStatement parameter would be rejected. The target path
            // is fully application-controlled (timestamp + configured backup dir), never
            // user input. The single-quote escaping below defends against path quoting
            // breakage (paths with apostrophes) rather than SQL injection.
            stmt.execute("VACUUM INTO '" + backupFile.toString().replace("'", "''") + "'");
        }

        return backupFile;
    }

    public List<Path> listBackups() throws IOException {
        if (!Files.exists(backupDir)) return Collections.emptyList();

        try (Stream<Path> stream = Files.list(backupDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".db"))
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        }
    }

    public void cleanOldBackups(int keepCount) throws IOException {
        List<Path> backups = listBackups();
        if (backups.size() <= keepCount) return;

        for (int i = keepCount; i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }
}
