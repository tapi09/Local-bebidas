package com.softwaredebebidas.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Repository for the app_config key/value table (dark mode, business name, etc.).
 */
public class ConfigRepository {

    /** Config key storing the business name shown in the UI and on receipts. */
    public static final String KEY_BUSINESS_NAME = "business_name";

    /** Generic business name used until the owner configures their own. */
    public static final String DEFAULT_BUSINESS_NAME = "Mi negocio";

    /** Config key storing the last directory used for backup export. */
    public static final String KEY_BACKUP_EXPORT_DIR = "backup_export_dir";

    private final DatabaseManager dbManager;

    public ConfigRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Returns the stored value for a config key, if present.
     */
    public Optional<String> get(String key) throws SQLException {
        String sql = "SELECT value FROM app_config WHERE key = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("value"));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Inserts or replaces the value for a config key.
     */
    public void set(String key, String value) throws SQLException {
        String sql = "INSERT OR REPLACE INTO app_config (key, value) VALUES (?, ?)";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    /**
     * Returns the configured business name, or {@link #DEFAULT_BUSINESS_NAME}
     * when none is stored or the stored value is blank.
     */
    public String getBusinessName() throws SQLException {
        return get(KEY_BUSINESS_NAME)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .orElse(DEFAULT_BUSINESS_NAME);
    }

    /**
     * Persists the business name (trimmed).
     */
    public void setBusinessName(String name) throws SQLException {
        set(KEY_BUSINESS_NAME, name.trim());
    }

    /**
     * Returns the last directory used for backup export, if one was persisted.
     */
    public Optional<String> getBackupExportDir() throws SQLException {
        return get(KEY_BACKUP_EXPORT_DIR);
    }

    /**
     * Persists the last directory used for backup export.
     */
    public void setBackupExportDir(String directory) throws SQLException {
        set(KEY_BACKUP_EXPORT_DIR, directory);
    }
}
