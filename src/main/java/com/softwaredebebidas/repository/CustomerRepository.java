package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.Customer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para operaciones CRUD de Customer.
 * Usa JDBC puro con PreparedStatement para el acceso a datos.
 */
public class CustomerRepository {

    private final DatabaseManager dbManager;

    public CustomerRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Guarda un nuevo cliente y retorna su ID generado.
     */
    public Long save(Customer customer) throws SQLException {
        String sql = "INSERT INTO customers (name, phone, email, address) VALUES (?, ?, ?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
            ps.setString(4, customer.getAddress());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("No se pudo obtener el ID generado del cliente");
            }
        }
    }

    /**
     * Busca un cliente por su ID.
     */
    public Optional<Customer> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM customers WHERE id = ?";
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
     * Retorna todos los clientes ordenados por nombre.
     */
    public List<Customer> findAll() throws SQLException {
        String sql = "SELECT * FROM customers ORDER BY name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Customer> customers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                customers.add(mapRow(rs));
            }
        }
        return customers;
    }

    /**
     * Actualiza un cliente existente.
     */
    public void update(Customer customer) throws SQLException {
        String sql = "UPDATE customers SET name = ?, phone = ?, email = ?, address = ? WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
            ps.setString(4, customer.getAddress());
            ps.setLong(5, customer.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Elimina un cliente por su ID.
     */
    public void delete(Long id) throws SQLException {
        String sql = "DELETE FROM customers WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Busca clientes por nombre (búsqueda parcial, case-insensitive).
     */
    public List<Customer> searchByName(String query) throws SQLException {
        String sql = "SELECT * FROM customers WHERE UPPER(name) LIKE UPPER(?) ORDER BY name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Customer> customers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    customers.add(mapRow(rs));
                }
            }
        }
        return customers;
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setId(rs.getLong("id"));
        customer.setName(rs.getString("name"));
        customer.setPhone(rs.getString("phone"));
        customer.setEmail(rs.getString("email"));
        customer.setAddress(rs.getString("address"));
        customer.setCreatedAt(rs.getString("created_at"));
        return customer;
    }
}
