package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.Supplier;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Supplier CRUD operations.
 * Uses raw JDBC with PreparedStatement for data access.
 */
public class SupplierRepository {

    private final DatabaseManager dbManager;

    public SupplierRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Saves a new supplier and returns its generated ID.
     */
    public Long save(Supplier supplier) throws SQLException {
        String sql = "INSERT INTO suppliers (name, contact, phone, email, address) VALUES (?, ?, ?, ?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, supplier.getName());
            ps.setString(2, supplier.getContact());
            ps.setString(3, supplier.getPhone());
            ps.setString(4, supplier.getEmail());
            ps.setString(5, supplier.getAddress());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("Failed to retrieve generated supplier ID");
            }
        }
    }

    /**
     * Finds a supplier by ID.
     */
    public Optional<Supplier> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM suppliers WHERE id = ?";
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
     * Returns all suppliers ordered by name.
     */
    public List<Supplier> findAll() throws SQLException {
        String sql = "SELECT * FROM suppliers ORDER BY name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Supplier> suppliers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                suppliers.add(mapRow(rs));
            }
        }
        return suppliers;
    }

    /**
     * Returns suppliers for dropdown selection (id + name only).
     */
    public List<Supplier> findForDropdown() throws SQLException {
        String sql = "SELECT id, name FROM suppliers ORDER BY name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Supplier> suppliers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Supplier s = new Supplier();
                s.setId(rs.getLong("id"));
                s.setName(rs.getString("name"));
                suppliers.add(s);
            }
        }
        return suppliers;
    }

    /**
     * Updates an existing supplier.
     */
    public void update(Supplier supplier) throws SQLException {
        String sql = "UPDATE suppliers SET name = ?, contact = ?, phone = ?, email = ?, address = ? WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, supplier.getName());
            ps.setString(2, supplier.getContact());
            ps.setString(3, supplier.getPhone());
            ps.setString(4, supplier.getEmail());
            ps.setString(5, supplier.getAddress());
            ps.setLong(6, supplier.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Deletes a supplier by ID.
     */
    public void delete(Long id) throws SQLException {
        String sql = "DELETE FROM suppliers WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private Supplier mapRow(ResultSet rs) throws SQLException {
        Supplier supplier = new Supplier();
        supplier.setId(rs.getLong("id"));
        supplier.setName(rs.getString("name"));
        supplier.setContact(rs.getString("contact"));
        supplier.setPhone(rs.getString("phone"));
        supplier.setEmail(rs.getString("email"));
        supplier.setAddress(rs.getString("address"));
        supplier.setCreatedAt(rs.getString("created_at"));
        return supplier;
    }
}
