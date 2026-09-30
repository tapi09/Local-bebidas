package com.softwaredebebidas.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves the application data directory ({@code %APPDATA%/SoftwareDeBebidas})
 * and migrates data left by installations up to 1.0.3, which used a different
 * directory and database file name.
 *
 * <p>The migration copies the legacy directory into a staging directory and then
 * renames it atomically, so an interrupted copy never leaves a half-populated data
 * directory behind. The legacy directory is never modified or deleted: it stays as
 * a safety copy. If the copy fails, the application keeps running on the legacy
 * directory so the store can keep operating with its existing data.</p>
 */
public final class AppDataDir {

    private static final Logger LOGGER = Logger.getLogger(AppDataDir.class.getName());

    public static final String DIR_NAME = "SoftwareDeBebidas";
    public static final String DB_FILE = "softwaredebebidas.db";
    public static final String LOCK_FILE = "softwaredebebidas.lock";

    // Names used by installations up to 1.0.3; referenced only to migrate them.
    static final String LEGACY_DIR_NAME = "Cocolatan";
    static final String LEGACY_DB_FILE = "cocolatan.db";
    static final String LEGACY_LOCK_FILE = "cocolatan.lock";

    static final String STAGING_SUFFIX = ".migrating";

    private static volatile Path current;

    /** Copies a directory tree; replaceable in tests to simulate I/O failures. */
    @FunctionalInterface
    interface TreeCopier {
        void copy(Path from, Path to) throws IOException;
    }

    private AppDataDir() {
    }

    /**
     * Resolves the data directory under {@code appDataRoot}, migrating legacy data
     * first when needed, and makes it the directory returned by {@link #getBaseDir()}.
     */
    public static Path initialize(Path appDataRoot) {
        current = migrateLegacy(appDataRoot);
        return current;
    }

    /**
     * Returns the data directory chosen by {@link #initialize(Path)}, or the default
     * location when the application has not been initialized (tests, tooling).
     */
    public static Path getBaseDir() {
        Path dir = current;
        if (dir != null) {
            return dir;
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, DIR_NAME);
        }
        return Paths.get("data");
    }

    /**
     * Returns the database file inside {@code dataDir}: the current name, unless only
     * a legacy database exists there (fallback after a failed migration).
     */
    public static Path databaseFile(Path dataDir) {
        Path db = dataDir.resolve(DB_FILE);
        Path legacyDb = dataDir.resolve(LEGACY_DB_FILE);
        if (!Files.exists(db) && Files.exists(legacyDb)) {
            return legacyDb;
        }
        return db;
    }

    static Path migrateLegacy(Path appDataRoot) {
        return migrateLegacy(appDataRoot, AppDataDir::copyTree);
    }

    static Path migrateLegacy(Path appDataRoot, TreeCopier copier) {
        Path target = appDataRoot.resolve(DIR_NAME);
        Path legacy = appDataRoot.resolve(LEGACY_DIR_NAME);
        if (Files.exists(target) || !Files.isDirectory(legacy)) {
            return target;
        }

        Path staging = appDataRoot.resolve(DIR_NAME + STAGING_SUFFIX);
        try {
            deleteTree(staging);
            copier.copy(legacy, staging);
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Migrated application data from " + legacy + " to " + target
                    + "; the previous directory is kept as a safety copy");
            return target;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not migrate application data from " + legacy
                    + "; continuing with the existing directory", e);
            try {
                deleteTree(staging);
            } catch (IOException cleanup) {
                LOGGER.log(Level.WARNING, "Could not remove staging directory " + staging, cleanup);
            }
            return legacy;
        }
    }

    /**
     * Copies {@code from} into {@code to}, renaming the top-level legacy database
     * files (including SQLite -wal/-shm/-journal companions) and skipping the
     * legacy single-instance lock file.
     */
    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = from.relativize(file);
                String name = file.getFileName().toString();
                boolean topLevel = relative.getNameCount() == 1;
                if (topLevel && name.equals(LEGACY_LOCK_FILE)) {
                    return FileVisitResult.CONTINUE;
                }
                Path destination = to.resolve(relative.toString());
                if (topLevel && name.startsWith(LEGACY_DB_FILE)) {
                    destination = to.resolve(DB_FILE + name.substring(LEGACY_DB_FILE.length()));
                }
                Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
