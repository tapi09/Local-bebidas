package com.softwaredebebidas.util;

import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for locating, storing and loading the business logo.
 * <p>
 * The logo is chosen by the owner from the "Datos del negocio" screen and lives at
 * {@code <data dir>/logo/logo.png}. Any supported image (PNG, JPG, GIF, BMP) is
 * normalized to PNG when stored. The application ships no logo of its own and never
 * writes or overwrites this file on its own: when it does not exist the UI shows the
 * business name instead.
 */
public final class LogoUtils {

    private static final Logger LOGGER = Logger.getLogger(LogoUtils.class.getName());

    private LogoUtils() {
    }

    public static Path getBaseDir() {
        return AppDataDir.getBaseDir();
    }

    public static Path logoPathFor(Path baseDir) {
        return baseDir.resolve("logo").resolve("logo.png");
    }

    public static Path getLogoPath() {
        return logoPathFor(getBaseDir());
    }

    /** Returns the stored logo under {@code baseDir}, or {@code null} when there is none. */
    public static Path resolveLogoPath(Path baseDir) {
        Path logo = logoPathFor(baseDir);
        return Files.exists(logo) ? logo : null;
    }

    /** Returns the stored logo, or {@code null} when none has been chosen. */
    public static Path resolveLogoPath() {
        return resolveLogoPath(getBaseDir());
    }

    public static void saveLogo(Path source) throws IOException {
        saveLogo(getBaseDir(), source);
    }

    /**
     * Decodes {@code source} and stores it as PNG at the logo location under
     * {@code baseDir}, replacing any previous logo. The current logo is left
     * untouched when the file cannot be decoded as an image.
     *
     * @throws IOException if the file is not a readable image or cannot be written
     */
    public static void saveLogo(Path baseDir, Path source) throws IOException {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) {
            throw new IOException("Unsupported image format: " + source.getFileName());
        }
        Path target = logoPathFor(baseDir);
        Files.createDirectories(target.getParent());
        Path staging = target.resolveSibling("logo.png.tmp");
        try {
            if (!ImageIO.write(image, "png", staging.toFile())) {
                throw new IOException("Could not encode logo as PNG");
            }
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    public static void removeLogo() throws IOException {
        removeLogo(getBaseDir());
    }

    public static void removeLogo(Path baseDir) throws IOException {
        Files.deleteIfExists(logoPathFor(baseDir));
    }

    /**
     * Loads the stored logo as a JavaFX image, or returns {@code null} when there is
     * no logo or it cannot be decoded. Reads the bytes directly so a logo replaced
     * while the app runs is never served from a cache. Never throws.
     */
    public static Image loadLogoImage() {
        try {
            Path path = resolveLogoPath();
            if (path == null || !Files.isReadable(path)) {
                return null;
            }
            Image image = new Image(new ByteArrayInputStream(Files.readAllBytes(path)));
            return image.isError() ? null : image;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load logo", e);
            return null;
        }
    }
}
