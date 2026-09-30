package com.softwaredebebidas.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Single source of truth for the application data directory
 * ({@code %APPDATA%/software-bebidas}) and the well-known file names inside it.
 *
 * <p>Database, lock file, backups, exports, logs, logo and photos all resolve their
 * location from {@link #getBaseDir()}. When {@code APPDATA} is not set (tests,
 * tooling, non-Windows hosts) the base directory falls back to a local {@code data}
 * folder.</p>
 */
public final class AppDataDir {

    public static final String DIR_NAME = "software-bebidas";
    public static final String DB_FILE = "software-bebidas.db";
    public static final String LOCK_FILE = "software-bebidas.lock";

    private static final String FALLBACK_DIR = "data";

    private static volatile Path current;

    private AppDataDir() {
    }

    /**
     * Resolves the data directory under {@code appDataRoot} and makes it the
     * directory returned by {@link #getBaseDir()}.
     */
    public static Path initialize(Path appDataRoot) {
        current = appDataRoot.resolve(DIR_NAME);
        return current;
    }

    /**
     * Returns the data directory chosen by {@link #initialize(Path)}, or the default
     * location when the application has not been initialized (tests, tooling).
     */
    public static Path getBaseDir() {
        Path dir = current;
        return dir != null ? dir : defaultDir(System.getenv("APPDATA"));
    }

    static Path defaultDir(String appData) {
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, DIR_NAME);
        }
        return Paths.get(FALLBACK_DIR);
    }

    public static Path databaseFile(Path dataDir) {
        return dataDir.resolve(DB_FILE);
    }

    public static Path lockFile(Path dataDir) {
        return dataDir.resolve(LOCK_FILE);
    }

    /** True when no database exists yet in {@code baseDir} (first start). */
    public static boolean needsSetup(Path baseDir) {
        return !Files.exists(databaseFile(baseDir));
    }

    /**
     * Copies a previous installation folder into {@code baseDir}, skipping lock files,
     * and stores its single {@code .db} file under the current database name. The
     * source is never modified.
     */
    public static void importFrom(Path sourceDir, Path baseDir) throws IOException {
        Path sourceDb;
        try (Stream<Path> top = Files.list(sourceDir)) {
            List<Path> dbs = top.filter(p -> p.getFileName().toString().endsWith(".db")).toList();
            if (dbs.size() != 1) {
                throw new IOException("Expected exactly one .db file in " + sourceDir + " but found " + dbs.size());
            }
            sourceDb = dbs.get(0);
        }
        try (Stream<Path> tree = Files.walk(sourceDir)) {
            for (Path from : (Iterable<Path>) tree::iterator) {
                if (from.getFileName().toString().endsWith(".lock")) {
                    continue;
                }
                Path to = importTarget(from, sourceDb, baseDir, sourceDir);
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Maps a source file to its destination. The database and its SQLite companion
     * files (WAL, shared memory, rollback journal) take the current database name, so
     * transactions still in the WAL are not lost.
     */
    private static Path importTarget(Path from, Path sourceDb, Path baseDir, Path sourceDir) {
        if (from.getParent() != null && from.getParent().equals(sourceDir)) {
            String name = from.getFileName().toString();
            String dbName = sourceDb.getFileName().toString();
            for (String suffix : new String[] {"", "-wal", "-shm", "-journal"}) {
                if (name.equals(dbName + suffix)) {
                    return baseDir.resolve(DB_FILE + suffix);
                }
            }
        }
        return baseDir.resolve(sourceDir.relativize(from).toString());
    }

    /** Test hook: forgets the initialized directory. */
    static void reset() {
        current = null;
    }
}
