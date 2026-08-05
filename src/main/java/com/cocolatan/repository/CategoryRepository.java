package com.cocolatan.repository;

import com.cocolatan.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Category CRUD operations, ordering and orphan-safe deletion.
 * Uses raw JDBC with PreparedStatement for data access.
 */
public class CategoryRepository {

    private final DatabaseManager dbManager;

    public CategoryRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Saves a new category and returns its generated ID (also written back on the entity).
     * Throws SQLException if the name is duplicated.
     */
    public Long save(Category category) throws SQLException {
        String sql = "INSERT INTO categories (name, sort_order, active) VALUES (?, ?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setInt(2, category.getSortOrder());
            ps.setBoolean(3, category.isActive());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    category.setId(id);
                    return id;
                }
                throw new SQLException("Failed to retrieve generated category ID");
            }
        }
    }

    /**
     * Finds a category by ID.
     */
    public Optional<Category> findById(long id) throws SQLException {
        String sql = "SELECT * FROM categories WHERE id = ?";
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
     * Returns all categories ordered by sort_order, then name (case-insensitive).
     */
    public List<Category> findAll() throws SQLException {
        String sql = "SELECT * FROM categories ORDER BY sort_order ASC, name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Category> categories = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(mapRow(rs));
            }
        }
        return categories;
    }

    /**
     * Returns only active categories ordered by sort_order, then name (case-insensitive).
     */
    public List<Category> findAllActive() throws SQLException {
        String sql = "SELECT * FROM categories WHERE active = 1 ORDER BY sort_order ASC, name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Category> categories = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(mapRow(rs));
            }
        }
        return categories;
    }

    /**
     * Updates an existing category (name, sort_order, active).
     */
    public void update(Category category) throws SQLException {
        String sql = "UPDATE categories SET name = ?, sort_order = ?, active = ? WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setInt(2, category.getSortOrder());
            ps.setBoolean(3, category.isActive());
            ps.setLong(4, category.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Activates or deactivates a category.
     */
    public void setActive(long id, boolean active) throws SQLException {
        String sql = "UPDATE categories SET active = ? WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * Returns whether a category with the given name already exists.
     */
    public boolean existsByName(String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM categories WHERE name = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Counts products referencing this category, including those whose
     * subcategory belongs to this category (they keep category_id set).
     */
    public int countProductsByCategory(long categoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE category_id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Deletes a category in a single transaction: its products are orphaned
     * (category_id/subcategory_id NULL and legacy category set to ''),
     * then its subcategories and the category itself are removed.
     */
    public void deleteCategoryAndOrphans(long categoryId) throws SQLException {
        Connection conn = dbManager.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE products SET category_id = NULL, subcategory_id = NULL, category = '' WHERE category_id = ?")) {
                ps.setLong(1, categoryId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM subcategories WHERE category_id = ?")) {
                ps.setLong(1, categoryId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM categories WHERE id = ?")) {
                ps.setLong(1, categoryId);
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

    private Category mapRow(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setId(rs.getLong("id"));
        category.setName(rs.getString("name"));
        category.setSortOrder(rs.getInt("sort_order"));
        category.setActive(rs.getBoolean("active"));
        category.setCreatedAt(rs.getString("created_at"));
        return category;
    }
}
