package com.cocolatan.repository;

import com.cocolatan.model.StockMovement;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for StockMovement operations.
 * Handles insert, query by product, and current stock computation.
 */
public class StockMovementRepository {

    private final DatabaseManager dbManager;

    public StockMovementRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Inserts a stock movement and returns its generated ID.
     */
    public Long insert(StockMovement movement) throws SQLException {
        Connection conn = dbManager.getConnection();
        return insert(conn, movement);
    }

    /**
     * Inserts a stock movement using an existing connection.
     * Does NOT manage transactions — the caller controls commit/rollback.
     */
    public Long insert(Connection conn, StockMovement movement) throws SQLException {
        String sql = "INSERT INTO stock_movements (product_id, movement_type, quantity, reference_type, reference_id, notes) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, movement.getProductId());
            ps.setString(2, movement.getMovementType());
            ps.setInt(3, movement.getQuantity());
            ps.setString(4, movement.getReferenceType());
            if (movement.getReferenceId() != null) {
                ps.setLong(5, movement.getReferenceId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setString(6, movement.getNotes());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("Failed to retrieve generated stock movement ID");
            }
        }
    }

    /**
     * Returns all movements for a product, ordered by date descending.
     */
    public List<StockMovement> findByProductId(Long productId) throws SQLException {
        String sql = "SELECT * FROM stock_movements WHERE product_id = ? ORDER BY created_at DESC";
        Connection conn = dbManager.getConnection();
        List<StockMovement> movements = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    movements.add(mapRow(rs));
                }
            }
        }
        return movements;
    }

    /**
     * Returns movements for a product filtered by type.
     */
    public List<StockMovement> findByProductIdAndType(Long productId, String movementType) throws SQLException {
        String sql = "SELECT * FROM stock_movements WHERE product_id = ? AND movement_type = ? ORDER BY created_at DESC";
        Connection conn = dbManager.getConnection();
        List<StockMovement> movements = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, productId);
            ps.setString(2, movementType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    movements.add(mapRow(rs));
                }
            }
        }
        return movements;
    }

    /**
     * Computes current stock for a product.
     * Formula: SUM(ENTRY qty) - SUM(EXIT qty).
     * ADJUSTMENT movements are treated as ENTRY (add).
     */
    public int computeCurrentStock(Long productId) throws SQLException {
        String sql = "SELECT "
                + "COALESCE(SUM(CASE WHEN movement_type IN ('ENTRY', 'ADJUSTMENT') THEN quantity ELSE 0 END), 0) "
                + "- COALESCE(SUM(CASE WHEN movement_type = 'EXIT' THEN quantity ELSE 0 END), 0) "
                + "AS current_stock "
                + "FROM stock_movements WHERE product_id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("current_stock");
                }
                return 0;
            }
        }
    }

    /**
     * Computes current stock for multiple products in a single query.
     * Returns a map of productId -> currentStock. Products not in the map have 0 stock.
     */
    public java.util.Map<Long, Integer> computeCurrentStocks(List<Long> productIds) throws SQLException {
        java.util.Map<Long, Integer> stocks = new java.util.HashMap<>();
        if (productIds.isEmpty()) {
            return stocks;
        }
        String placeholders = productIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT product_id, "
                + "COALESCE(SUM(CASE WHEN movement_type IN ('ENTRY','ADJUSTMENT') THEN quantity ELSE 0 END), 0) "
                + "- COALESCE(SUM(CASE WHEN movement_type = 'EXIT' THEN quantity ELSE 0 END), 0) AS stock "
                + "FROM stock_movements WHERE product_id IN (" + placeholders + ") GROUP BY product_id";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < productIds.size(); i++) {
                ps.setLong(i + 1, productIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stocks.put(rs.getLong("product_id"), rs.getInt("stock"));
                }
            }
        }
        return stocks;
    }

    /**
     * Returns movements matching optional filters. All filters are AND-combined.
     *
     * @param productId    filter by product (null = all products)
     * @param movementType filter by type (null or empty = all types)
     * @param fromDate     filter created_at >= fromDate in yyyy-MM-dd or dd/MM/yyyy (null = no lower bound)
     * @param toDate       filter created_at <= toDate in yyyy-MM-dd or dd/MM/yyyy (null = no upper bound)
     */
    public List<StockMovement> findByFilters(Long productId, String movementType,
                                              String fromDate, String toDate) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM stock_movements WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (productId != null) {
            sql.append(" AND product_id = ?");
            params.add(productId);
        }
        if (movementType != null && !movementType.isEmpty()) {
            sql.append(" AND movement_type = ?");
            params.add(movementType);
        }
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append(" AND created_at >= ?");
            params.add(normalizeDate(fromDate));
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append(" AND created_at <= ?");
            params.add(normalizeDate(toDate) + " 23:59:59");
        }
        sql.append(" ORDER BY created_at DESC");

        Connection conn = dbManager.getConnection();
        List<StockMovement> movements = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Long l) {
                    ps.setLong(i + 1, l);
                } else if (param instanceof Integer n) {
                    ps.setInt(i + 1, n);
                } else {
                    ps.setString(i + 1, param.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    movements.add(mapRow(rs));
                }
            }
        }
        return movements;
    }

    /**
     * Normalizes a date string from dd/MM/yyyy to yyyy-MM-dd for SQLite comparison.
     */
    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return dateStr;
        }
        if (dateStr.contains("/")) {
            String[] parts = dateStr.split("/");
            if (parts.length == 3) {
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            }
        }
        return dateStr;
    }

    private StockMovement mapRow(ResultSet rs) throws SQLException {
        StockMovement movement = new StockMovement();
        movement.setId(rs.getLong("id"));
        movement.setProductId(rs.getLong("product_id"));
        movement.setMovementType(rs.getString("movement_type"));
        movement.setQuantity(rs.getInt("quantity"));
        movement.setReferenceType(rs.getString("reference_type"));
        long referenceId = rs.getLong("reference_id");
        if (!rs.wasNull()) {
            movement.setReferenceId(referenceId);
        }
        movement.setNotes(rs.getString("notes"));
        movement.setCreatedAt(rs.getString("created_at"));
        return movement;
    }
}
