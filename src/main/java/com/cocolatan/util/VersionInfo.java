package com.cocolatan.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads application version metadata from the classpath resource
 * {@code /version.properties} and exposes it without ever throwing.
 * Missing, partial or malformed metadata falls back to the literal value
 * {@code unknown}.
 */
public final class VersionInfo {

    private static final String UNKNOWN = "unknown";

    private static final VersionInfo INSTANCE = load();

    private final String version;
    private final String buildDate;

    private VersionInfo(String version, String buildDate) {
        this.version = version;
        this.buildDate = buildDate;
    }

    /**
     * Pure factory over a {@link Properties} map. Null, missing or blank
     * values fall back to {@code unknown}.
     */
    static VersionInfo fromProperties(Properties props) {
        if (props == null) {
            return new VersionInfo(UNKNOWN, UNKNOWN);
        }
        return new VersionInfo(
                valueOrUnknown(props.getProperty("app.version")),
                valueOrUnknown(props.getProperty("app.build.date")));
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value.trim();
    }

    private static VersionInfo load() {
        try (InputStream in = VersionInfo.class.getResourceAsStream("/version.properties")) {
            if (in == null) {
                return fromProperties(null);
            }
            Properties props = new Properties();
            props.load(in);
            return fromProperties(props);
        } catch (Exception e) {
            return fromProperties(null);
        }
    }

    /**
     * Current application version, or {@code unknown} when metadata is missing
     * or malformed.
     */
    public static String getVersion() {
        return INSTANCE.version();
    }

    /**
     * Build timestamp of the current application, or {@code unknown} when
     * metadata is missing or malformed.
     */
    public static String getBuildDate() {
        return INSTANCE.buildDate();
    }

    /**
     * Combined display label: {@code version (date)}, {@code version} or
     * {@code unknown}.
     */
    public static String getDisplayString() {
        return INSTANCE.displayString();
    }

    String version() {
        return version;
    }

    String buildDate() {
        return buildDate;
    }

    String displayString() {
        boolean versionKnown = !UNKNOWN.equals(version);
        boolean dateKnown = !UNKNOWN.equals(buildDate);
        if (versionKnown && dateKnown) {
            return version + " (" + buildDate + ")";
        }
        if (versionKnown) {
            return version;
        }
        if (dateKnown) {
            return UNKNOWN + " (" + buildDate + ")";
        }
        return UNKNOWN;
    }
}
