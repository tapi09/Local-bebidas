package com.cocolatan.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Utility for managing product photos stored relative to the app's data directory.
 * Photos live in {@code %APPDATA%/Cocolatan/product-photos/} co-located with DB and logs.
 */
public class PhotoUtils {

    private PhotoUtils() {
    }

    public static Path getBaseDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "Cocolatan");
        }
        return Paths.get("data");
    }

    public static Path getPhotosDir() {
        return getBaseDir().resolve("product-photos");
    }

    public static Path getInvoicePhotosDir() {
        return getBaseDir().resolve("purchase-invoices");
    }

    /**
     * Ensures the photo directory exists, creating it if needed.
     */
    public static void ensureDir() {
        try {
            Files.createDirectories(getPhotosDir());
        } catch (IOException e) {
            throw new RuntimeException("Could not create photo directory: " + getPhotosDir(), e);
        }
    }

    /**
     * Copies a source image file into the product-photos directory and returns the
     * relative path (e.g. {@code product-photos/uuid-foo.jpg}).
     */
    public static String copyImage(java.io.File source) {
        ensureDir();
        String ext = extractExtension(source.getName());
        String filename = UUID.randomUUID() + ext;
        Path target = getPhotosDir().resolve(filename);
        try {
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy product photo: " + source, e);
        }
        return "product-photos/" + filename;
    }

    /**
     * Copies a source image file into the purchase-invoices directory and returns the
     * relative path (e.g. {@code purchase-invoices/uuid-foo.jpg}).
     */
    public static String copyInvoiceImage(java.io.File source) {
        ensureInvoiceDir();
        String ext = extractExtension(source.getName());
        String filename = UUID.randomUUID() + ext;
        Path target = getInvoicePhotosDir().resolve(filename);
        try {
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy invoice image: " + source, e);
        }
        return "purchase-invoices/" + filename;
    }

    /**
     * Ensures the invoice photo directory exists, creating it if needed.
     */
    public static void ensureInvoiceDir() {
        try {
            Files.createDirectories(getInvoicePhotosDir());
        } catch (IOException e) {
            throw new RuntimeException("Could not create invoice photo directory: " + getInvoicePhotosDir(), e);
        }
    }

    /**
     * Returns the absolute {@code Path} for a given relative photo path
     * (e.g. {@code product-photos/uuid-foo.jpg}), or {@code null} if the path is blank or file missing.
     */
    public static Path resolvePath(String photoPath) {
        if (photoPath == null || photoPath.isBlank()) {
            return null;
        }
        Path p = getBaseDir().resolve(photoPath);
        if (Files.exists(p)) {
            return p.toAbsolutePath();
        }
        // Fallback for legacy relative data folder
        Path legacy = Paths.get("data", photoPath);
        return Files.exists(legacy) ? legacy.toAbsolutePath() : null;
    }

    private static String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(dot) : "";
    }
}
