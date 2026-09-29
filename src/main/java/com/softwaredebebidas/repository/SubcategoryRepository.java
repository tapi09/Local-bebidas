package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.Subcategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Subcategory CRUD operations, scoping and orphan-safe deletion.
 * Uses raw JDBC with PreparedStatement for data access.
 */
public class SubcategoryRepository {

    private final DatabaseManager dbManager;

    public SubcategoryRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Saves a new subcategory and returns its generated ID (also written back on the entity).
     * Throws SQLException if the name is duplicated within the same category.
     */
    public Long save(Subcategory subcategory) throws SQLException {
        String sql = "INSERT INTO subcategories (category_id, name, sort_order, active) VALUES (?, ?, ?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, subcategory.getCategoryId());
            ps.setString(2, subcategory.getName());
            ps.setInt(3, subcategory.getSortOrder());
            ps.setBoolean(4, subcategory.isActive());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    subcategory.setId(id);
                    return id;
                }
                throw new SQLException("Failed to retrieve generated subcategory ID");
            }
        }
    }

    /**
     * Finds a subcategory by ID.
     */
    public Optional<Subcategory> findById(long id) throws SQLException {
        String sql = "SELECT * FROM subcategories WHERE id = ?";
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

    /**
     * Returns all subcategories of a category, ordered by sort_order, then name (case-insensitive).
     */
    public List<Subcategory> findAllByCategoryId(long categoryId) throws SQLException {
        String sql = "SELECT * FROM subcategories WHERE category_id = ? ORDER BY sort_order ASC, name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Subcategory> subcategories = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    subcategories.add(mapRow(rs));
                }
            }
        }
        return subcategories;
    }

    /**
     * Returns only active subcategories of a category, ordered by sort_order, then name (case-insensitive).
     */
    public List<Subcategory> findAllActiveByCategoryId(long categoryId) throws SQLException {
        String sql = "SELECT * FROM subcategories WHERE category_id = ? AND active = 1 ORDER BY sort_order ASC, name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Subcategory> subcategories = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    subcategories.add(mapRow(rs));
                }
            }
        }
        return subcategories;
    }

    /**
     * Updates an existing subcategory (category_id, name, sort_order, active).
     */
    public void update(Subcategory subcategory) throws SQLException {
        String sql = "UPDATE subcategories SET category_id = ?, name = ?, sort_order = ?, active = ? WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, subcategory.getCategoryId());
            ps.setString(2, subcategory.getName());
            ps.setInt(3, subcategory.getSortOrder());
            ps.setBoolean(4, subcategory.isActive());
            ps.setLong(5, subcategory.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Activates or deactivates a subcategory.
     */
    public void setActive(long id, boolean active) throws SQLException {
        String sql = "UPDATE subcategories SET active = ? WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * Returns whether a subcategory with the given name exists within a specific category.
     */
    public boolean existsByNameInCategory(long categoryId, String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM subcategories WHERE category_id = ? AND name = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Deletes a subcategory in a single transaction: its products are orphaned
     * (subcategory_id NULL) and the legacy category text is recomputed to the
     * category name alone; then the subcategory itself is removed.
     */
    public void deleteSubcategoryAndOrphans(long subcategoryId) throws SQLException {
        Connection conn = dbManager.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE products SET subcategory_id = NULL, category = COALESCE(("
                            + "SELECT c.name FROM categories c WHERE c.id = products.category_id), '') "
                            + "WHERE subcategory_id = ?")) {
                ps.setLong(1, subcategoryId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM subcategories WHERE id = ?")) {
                ps.setLong(1, subcategoryId);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    private Subcategory mapRow(ResultSet rs) throws SQLException {
        Subcategory subcategory = new Subcategory();
        subcategory.setId(rs.getLong("id"));
        subcategory.setCategoryId(rs.getLong("category_id"));
        subcategory.setName(rs.getString("name"));
        subcategory.setSortOrder(rs.getInt("sort_order"));
        subcategory.setActive(rs.getBoolean("active"));
        subcategory.setCreatedAt(rs.getString("created_at"));
        return subcategory;
    }
}
