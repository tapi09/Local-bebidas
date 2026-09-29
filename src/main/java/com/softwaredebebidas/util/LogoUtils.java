package com.softwaredebebidas.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for locating and seeding the brand logo.
 * <p>
 * The installed app reads the logo from the app's data directory
 * ({@code %APPDATA%/Cocolatan/logo/logo.png}), which is synced from the bundled
 * classpath resource on startup: the bundled logo replaces the data-dir file
 * in-place whenever it is missing or differs. Uploading a new bundled logo
 * therefore puts it exactly where the current one is.
 */
public final class LogoUtils {

    private static final Logger LOGGER = Logger.getLogger(LogoUtils.class.getName());

    private static final String LOGO_RESOURCE = "/icons/logo.png";
    private static final String LEGACY_LOGO_DIR = "data";

    private LogoUtils() {
    }

    public static Path getBaseDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "Cocolatan");
        }
        return Paths.get("data");
    }

    static Path logoPathFor(Path baseDir) {
        return baseDir.resolve("logo").resolve("logo.png");
    }

    public static Path getLogoPath() {
        return logoPathFor(getBaseDir());
    }

    static Path resolveLogoPath(Path baseDir, Path legacyLogo) {
        Path dataLogo = logoPathFor(baseDir);
        if (Files.exists(dataLogo)) {
            return dataLogo;
        }
        if (Files.exists(legacyLogo)) {
            return legacyLogo;
        }
        return null;
    }

    public static Path resolveLogoPath() {
        return resolveLogoPath(getBaseDir(), Path.of(LEGACY_LOGO_DIR, "logo", "logo.png"));
    }

    public static InputStream bundledLogoStream() {
        return LogoUtils.class.getResourceAsStream(LOGO_RESOURCE);
    }

    public static boolean ensureLogo() {
        return ensureLogo(getBaseDir(), bundledLogoStream());
    }

    static boolean ensureLogo(Path baseDir, InputStream bundled) {
        if (bundled == null) {
            return false;
        }
        Path target = logoPathFor(baseDir);
        try (InputStream in = bundled) {
            byte[] bundledBytes = in.readAllBytes();
            boolean missing = !Files.exists(target);
            boolean differs = !missing && !java.util.Arrays.equals(Files.readAllBytes(target), bundledBytes);
            if (missing || differs) {
                Files.createDirectories(target.getParent());
                Files.write(target, bundledBytes);
            }
            return Files.exists(target);
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Could not ensure logo at " + target, e);
            return false;
        }
    }

    public static java.io.File resolveLogoFile() {
        Path path = resolveLogoPath();
        return path == null ? null : path.toFile();
    }
}
