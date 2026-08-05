package com.cocolatan.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Repository for the app_config key/value table (dark mode, etc.).
 */
public class ConfigRepository {

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
}
