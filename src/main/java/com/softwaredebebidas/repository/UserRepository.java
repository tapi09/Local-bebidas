package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserRepository {

    private final DatabaseManager dbManager;

    public UserRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<User> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public void save(User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role, display_name, must_change_password) VALUES (?, ?, ?, ?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRole());
            ps.setString(4, user.getDisplayName());
            ps.setInt(5, user.isMustChangePassword() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    user.setId(rs.getLong(1));
                }
            }
        }
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM users ORDER BY username";
        Connection conn = dbManager.getConnection();
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    public void updatePassword(Long id, String newHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, must_change_password = 0 WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * Updates the mutable profile fields of an existing user. Never touches
     * username, password hash, or the must_change_password flag.
     */
    public void update(User user) throws SQLException {
        String sql = "UPDATE users SET display_name = ?, role = ? WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getDisplayName());
            ps.setString(2, user.getRole());
            ps.setLong(3, user.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Sets the forced password change flag for a user (e.g. after an admin resets
     * a password, or to clear it after the user changes their own password).
     */
    public void setMustChangePassword(Long id, boolean mustChange) throws SQLException {
        String sql = "UPDATE users SET must_change_password = ? WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mustChange ? 1 : 0);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void delete(Long id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(rs.getString("role"));
        user.setDisplayName(rs.getString("display_name"));
        user.setCreatedAt(rs.getString("created_at"));
        user.setMustChangePassword(rs.getInt("must_change_password") != 0);
        return user;
    }
}
