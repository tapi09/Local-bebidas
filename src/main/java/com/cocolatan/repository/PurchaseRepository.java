package com.cocolatan.repository;

import com.cocolatan.model.Purchase;
import com.cocolatan.model.PurchaseItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Purchase operations with transaction support.
 * Saves purchase + items atomically, and queries purchase history.
 */
public class PurchaseRepository {

    private final DatabaseManager dbManager;

    public PurchaseRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Saves a purchase with its items in a single transaction.
     * Calculates total_amount from items automatically.
     *
     * @return the generated purchase ID
     */
    public Long saveWithItems(Purchase purchase, List<PurchaseItem> items) throws SQLException {
        Connection conn = dbManager.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            Long purchaseId = saveWithItems(conn, purchase, items);
            conn.commit();
            return purchaseId;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * Saves a purchase with its items using an existing connection.
     * Does NOT manage transactions — the caller controls commit/rollback.
     *
     * @return the generated purchase ID
     */
    public Long saveWithItems(Connection conn, Purchase purchase, List<PurchaseItem> items) throws SQLException {
        double subtotal = 0;
        for (PurchaseItem item : items) {
            subtotal += item.getQuantity() * item.getUnitCost();
        }
        purchase.setSubtotal(subtotal);
        purchase.setTotalAmount(subtotal + purchase.getTaxAmount());

        long purchaseId;
        String purchaseSql = "INSERT INTO purchases (supplier_id, invoice_ref, purchase_date, subtotal, tax_amount, total_amount, notes, invoice_photo_path, payment_method) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(purchaseSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, purchase.getSupplierId());
            ps.setString(2, purchase.getInvoiceRef());
            ps.setString(3, purchase.getPurchaseDate());
            ps.setDouble(4, purchase.getSubtotal());
            ps.setDouble(5, purchase.getTaxAmount());
            ps.setDouble(6, purchase.getTotalAmount());
            ps.setString(7, purchase.getNotes());
            ps.setString(8, purchase.getInvoicePhotoPath());
            ps.setString(9, purchase.getPaymentMethod());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    purchaseId = rs.getLong(1);
                } else {
                    throw new SQLException("Failed to retrieve generated purchase ID");
                }
            }
        }

        String itemSql = "INSERT INTO purchase_items (purchase_id, product_id, quantity, unit_cost, lot_number, expiry_date) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
            for (PurchaseItem item : items) {
                ps.setLong(1, purchaseId);
                ps.setLong(2, item.getProductId());
                ps.setInt(3, item.getQuantity());
                ps.setDouble(4, item.getUnitCost());
                ps.setString(5, item.getLotNumber());
                ps.setString(6, item.getExpiryDate());
                ps.addBatch();
            }
            ps.executeBatch();
        }

        return purchaseId;
    }

    /**
     * Finds a purchase by ID.
     */
    public Optional<Purchase> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM purchases WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPurchase(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Returns purchase history sorted by date descending with pagination.
     *
     * @param limit  maximum number of results (0 = no limit)
     * @param offset number of results to skip (for pagination)
     */
    public List<Purchase> findHistory(int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM purchases ORDER BY id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ?");
        }
        if (offset > 0) {
            sql.append(" OFFSET ?");
        }
        Connection conn = dbManager.getConnection();
        List<Purchase> purchases = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            if (limit > 0) {
                ps.setInt(paramIndex++, limit);
            }
            if (offset > 0) {
                ps.setInt(paramIndex++, offset);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purchases.add(mapPurchase(rs));
                }
            }
        }
        // purchase_date is now ISO-8601 (YYYY-MM-DD), so SQL ORDER BY works correctly.
        // The Java sort is kept as a safety net for any legacy data.
        purchases.sort((a, b) -> {
            int c = compareDatesDesc(a.getPurchaseDate(), b.getPurchaseDate());
            if (c != 0) {
                return c;
            }
            return Long.compare(b.getId(), a.getId());
        });
        return purchases;
    }

    /**
     * Returns purchase history sorted by date descending.
     */
    public List<Purchase> findHistory() throws SQLException {
        return findHistory(0, 0);
    }

    private static final java.time.format.DateTimeFormatter DATE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Compares two YYYY-MM-DD date strings descending by date. Dates that cannot
     * be parsed sort last (treated as the oldest). Falls back to id ordering.
     */
    private static int compareDatesDesc(String a, String b) {
        try {
            java.time.LocalDate da = java.time.LocalDate.parse(a, DATE_FMT);
            try {
                java.time.LocalDate db = java.time.LocalDate.parse(b, DATE_FMT);
                return db.compareTo(da);
            } catch (Exception e) {
                return -1; // b unparseable -> b sorts after a
            }
        } catch (Exception e) {
            try {
                java.time.LocalDate.parse(b, DATE_FMT);
                return 1; // a unparseable -> a sorts after b
            } catch (Exception e2) {
                return 0; // both unparseable
            }
        }
    }

    /**
     * Returns all items for a given purchase.
     */
    public List<PurchaseItem> findItemsByPurchaseId(Long purchaseId) throws SQLException {
        String sql = "SELECT * FROM purchase_items WHERE purchase_id = ?";
        Connection conn = dbManager.getConnection();
        List<PurchaseItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, purchaseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapItem(rs));
                }
            }
        }
        return items;
    }

    /**
     * Returns all purchase items for a given product (across all purchases).
     */
    public List<PurchaseItem> findItemsByProductId(Long productId) throws SQLException {
        String sql = "SELECT * FROM purchase_items WHERE product_id = ?";
        Connection conn = dbManager.getConnection();
        List<PurchaseItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapItem(rs));
                }
            }
        }
        return items;
    }

    /**
     * Returns all purchase items for multiple products in a single query,
     * avoiding per-product round trips. Ordered by product_id for stable grouping.
     */
    public List<PurchaseItem> findItemsByProductIds(List<Long> productIds) throws SQLException {
        if (productIds == null || productIds.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = productIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT * FROM purchase_items WHERE product_id IN (" + placeholders + ") ORDER BY product_id";
        Connection conn = dbManager.getConnection();
        List<PurchaseItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < productIds.size(); i++) {
                ps.setLong(i + 1, productIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapItem(rs));
                }
            }
        }
        return items;
    }

    private Purchase mapPurchase(ResultSet rs) throws SQLException {
        Purchase purchase = new Purchase();
        purchase.setId(rs.getLong("id"));
        purchase.setSupplierId(rs.getLong("supplier_id"));
        purchase.setInvoiceRef(rs.getString("invoice_ref"));
        purchase.setPurchaseDate(rs.getString("purchase_date"));
        purchase.setSubtotal(rs.getDouble("subtotal"));
        purchase.setTaxAmount(rs.getDouble("tax_amount"));
        purchase.setTotalAmount(rs.getDouble("total_amount"));
        purchase.setNotes(rs.getString("notes"));
        purchase.setCreatedAt(rs.getString("created_at"));
        purchase.setInvoicePhotoPath(rs.getString("invoice_photo_path"));
        purchase.setPaymentMethod(rs.getString("payment_method"));
        return purchase;
    }

    private PurchaseItem mapItem(ResultSet rs) throws SQLException {
        PurchaseItem item = new PurchaseItem();
        item.setId(rs.getLong("id"));
        item.setPurchaseId(rs.getLong("purchase_id"));
        item.setProductId(rs.getLong("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitCost(rs.getDouble("unit_cost"));
        item.setLotNumber(rs.getString("lot_number"));
        item.setExpiryDate(rs.getString("expiry_date"));
        item.setCreatedAt(rs.getString("created_at"));
        return item;
    }
}
