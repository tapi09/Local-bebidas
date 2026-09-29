package com.softwaredebebidas.service;

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
        Path backupFile = backupDir.resolve("softwaredebebidas_backup_" + timestamp + ".db");

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

    /**
     * Copies the latest internal backup to the given directory.
     *
     * <p>The destination is validated first: it must be an absolute path with
     * no {@code .} / {@code ..} segments (path-traversal guard), must exist,
     * and must be a directory. When a file with the backup name already exists
     * in the destination, a numeric suffix is appended so earlier exports are
     * never overwritten.</p>
     *
     * @param destDir target directory (must exist, be absolute, and be writable)
     * @return the copied backup file path, or {@code null} if no backups exist
     * @throws IOException if the destination is invalid or the copy fails
     *                     (permissions, disk full, etc.)
     */
    public Path exportBackup(Path destDir) throws IOException {
        Path validated = validateDestDir(destDir);

        List<Path> backups = listBackups();
        if (backups.isEmpty()) {
            return null;
        }

        Path latest = backups.get(0);
        Path destination = uniqueDestination(validated, latest.getFileName());
        Files.copy(latest, destination);
        return destination;
    }

    /**
     * Validates the export destination directory. Rejects {@code null},
     * relative paths, and absolute paths containing {@code .} or {@code ..}
     * segments (the DirectoryChooser never produces these, so a crafted value
     * is a traversal attempt). Also requires the directory to exist, be a
     * directory, and be writable.
     *
     * @return the normalized destination path
     * @throws IOException when the destination is unsafe or unusable
     */
    static Path validateDestDir(Path destDir) throws IOException {
        if (destDir == null) {
            throw new IOException("Destination directory is null");
        }
        if (!destDir.isAbsolute()) {
            throw new IOException("Destination must be an absolute path: " + destDir);
        }
        Path normalized = destDir.normalize();
        if (!normalized.equals(destDir)) {
            throw new IOException("Destination path must not contain '..' segments: " + destDir);
        }
        if (!Files.isDirectory(normalized)) {
            throw new IOException("Destination is not a directory: " + normalized);
        }
        if (!Files.isWritable(normalized)) {
            throw new IOException("Destination directory is not writable: " + normalized);
        }
        return normalized;
    }

    /**
     * Picks a non-colliding destination file name inside {@code destDir} for
     * the given backup file name, appending {@code _1}, {@code _2}, ... before
     * the extension when the base name already exists.
     */
    static Path uniqueDestination(Path destDir, Path fileName) {
        String name = fileName.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";

        Path candidate = destDir.resolve(name);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = destDir.resolve(base + "_" + suffix + ext);
            suffix++;
        }
        return candidate;
    }

    public void cleanOldBackups(int keepCount) throws IOException {
        List<Path> backups = listBackups();
        if (backups.size() <= keepCount) return;

        for (int i = keepCount; i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }
}
