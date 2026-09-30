package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.service.SalesService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Sale operations with transaction support.
 * Saves sale + items atomically, and queries sale history.
 */
public class SaleRepository {

    private final DatabaseManager dbManager;

    public SaleRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Saves a sale with its items in a single transaction.
     * Calculates total_amount from items automatically.
     *
     * @return the generated sale ID
     */
    public Long saveWithItems(Sale sale, List<SaleItem> items) throws SQLException {
        Connection conn = dbManager.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            Long saleId = saveWithItems(conn, sale, items);
            conn.commit();
            return saleId;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * Saves a sale with its items using an existing connection.
     * Does NOT manage transactions — the caller controls commit/rollback.
     *
     * @return the generated sale ID
     */
    public Long saveWithItems(Connection conn, Sale sale, List<SaleItem> items) throws SQLException {
        double total = 0;
        for (SaleItem item : items) {
            double subtotal = item.getQuantity() * item.getUnitPrice();
            item.setSubtotal(subtotal);
            total += subtotal;
        }
        // Mirror SalePresenter.getDiscountedTotal semantics: a PERCENTAGE sale
        // discount is a percentage of the subtotal, a FIXED one is a flat amount.
        sale.setTotalAmount(round2(applySaleDiscount(total, sale)));

        long saleId;
        String saleSql = "INSERT INTO sales (sale_date, channel, payment_method, customer_id, discount, discount_type, total_amount) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(saleSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sale.getSaleDate());
            ps.setString(2, sale.getChannel());
            ps.setString(3, sale.getPaymentMethod());
            if (sale.getCustomerId() != null) {
                ps.setLong(4, sale.getCustomerId());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setDouble(5, sale.getDiscount());
            ps.setString(6, sale.getDiscountType());
            ps.setDouble(7, sale.getTotalAmount());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    saleId = rs.getLong(1);
                } else {
                    throw new SQLException("Failed to retrieve generated sale ID");
                }
            }
        }

        String itemSql = "INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, discount, discount_type, subtotal) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
            for (SaleItem item : items) {
                ps.setLong(1, saleId);
                ps.setLong(2, item.getProductId());
                ps.setInt(3, item.getQuantity());
                ps.setDouble(4, item.getUnitPrice());
                ps.setDouble(5, item.getDiscount());
                ps.setString(6, item.getDiscountType());
                ps.setDouble(7, item.getSubtotal());
                ps.addBatch();
            }
            ps.executeBatch();
        }

        return saleId;
    }

    /**
     * Applies the sale-level discount to a subtotal. PERCENTAGE discounts are a
     * percentage of the subtotal; FIXED discounts are a flat amount. Any other
     * type (including NONE) leaves the subtotal unchanged.
     *
     * <p>Mirrors {@code SalePresenter.getDiscountedTotal} validation (REQ-DISC-03 /
     * REQ-DISC-04): a negative discount, a PERCENTAGE above 100, or a FIXED
     * discount above the subtotal is rejected with a
     * {@link SalesService.ValidationException} before any INSERT happens. The
     * Math.max floor remains as defense-in-depth at the valid boundaries.</p>
     */
    private double applySaleDiscount(double subtotal, Sale sale) {
        double discount = sale.getDiscount();
        if ("PERCENTAGE".equals(sale.getDiscountType())) {
            if (discount < 0) {
                throw new SalesService.ValidationException("El descuento no puede ser negativo.");
            }
            if (discount > 100) {
                throw new SalesService.ValidationException("El descuento porcentual no puede superar el 100%.");
            }
            return Math.max(0, subtotal * (1 - discount / 100));
        }
        if ("FIXED".equals(sale.getDiscountType())) {
            if (discount < 0) {
                throw new SalesService.ValidationException("El descuento no puede ser negativo.");
            }
            if (discount > subtotal) {
                throw new SalesService.ValidationException("El descuento fijo no puede superar el subtotal de la venta.");
            }
            return Math.max(0, subtotal - discount);
        }
        return subtotal;
    }

    /**
     * Rounds a monetary amount to 2 decimal places.
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Finds a sale by ID.
     */
    public Optional<Sale> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM sales WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSale(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Returns sale history sorted by id descending (chronological).
     */
    public List<Sale> findHistory() throws SQLException {
        String sql = "SELECT * FROM sales ORDER BY id DESC";
        Connection conn = dbManager.getConnection();
        List<Sale> sales = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sales.add(mapSale(rs));
            }
        }
        return sales;
    }

    /**
     * Returns active sales within a date range, sorted by id descending.
     * Dates are in ISO-8601 format (YYYY-MM-DD) — direct comparison uses index.
     *
     * @param fromDate start date in YYYY-MM-DD format (inclusive)
     * @param toDate   end date in YYYY-MM-DD format (inclusive)
     * @param limit    maximum number of results (0 = no limit)
     * @param offset   number of results to skip (for pagination)
     */
    public List<Sale> findByDateRange(String fromDate, String toDate, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM sales WHERE status = 'ACTIVE' ")
                .append("AND sale_date BETWEEN ? AND ? ORDER BY id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ?");
        }
        if (offset > 0) {
            sql.append(" OFFSET ?");
        }
        Connection conn = dbManager.getConnection();
        List<Sale> sales = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            int paramIndex = 3;
            if (limit > 0) {
                ps.setInt(paramIndex++, limit);
            }
            if (offset > 0) {
                ps.setInt(paramIndex++, offset);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapSale(rs));
                }
            }
        }
        return sales;
    }

    /**
     * Returns active sales within a date range, sorted by id descending.
     * Dates are in ISO-8601 format (YYYY-MM-DD) — direct comparison uses index.
     *
     * @param fromDate start date in YYYY-MM-DD format (inclusive)
     * @param toDate   end date in YYYY-MM-DD format (inclusive)
     */
    public List<Sale> findByDateRange(String fromDate, String toDate) throws SQLException {
        return findByDateRange(fromDate, toDate, 0, 0);
    }

    /**
     * Returns all sales (including cancelled) within a date range, sorted by id descending.
     * Dates are in ISO-8601 format (YYYY-MM-DD) — direct comparison uses index.
     *
     * @param fromDate start date in YYYY-MM-DD format (inclusive)
     * @param toDate   end date in YYYY-MM-DD format (inclusive)
     * @param limit    maximum number of results (0 = no limit)
     * @param offset   number of results to skip (for pagination)
     */
    public List<Sale> findAllByDateRange(String fromDate, String toDate, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM sales ")
                .append("WHERE sale_date BETWEEN ? AND ? ORDER BY id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ?");
        }
        if (offset > 0) {
            sql.append(" OFFSET ?");
        }
        Connection conn = dbManager.getConnection();
        List<Sale> sales = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            int paramIndex = 3;
            if (limit > 0) {
                ps.setInt(paramIndex++, limit);
            }
            if (offset > 0) {
                ps.setInt(paramIndex++, offset);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapSale(rs));
                }
            }
        }
        return sales;
    }

    /**
     * Returns all sales (including cancelled) within a date range, sorted by id descending.
     * Dates are in ISO-8601 format (YYYY-MM-DD) — direct comparison uses index.
     *
     * @param fromDate start date in YYYY-MM-DD format (inclusive)
     * @param toDate   end date in YYYY-MM-DD format (inclusive)
     */
    public List<Sale> findAllByDateRange(String fromDate, String toDate) throws SQLException {
        return findAllByDateRange(fromDate, toDate, 0, 0);
    }

    /**
     * Returns the sum of total_amount for active sales on the given date.
     * Date uses ISO-8601 format (YYYY-MM-DD).
     */
    public double sumSalesForDate(String saleDate) throws SQLException {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM sales WHERE sale_date = ? AND status = 'ACTIVE'";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, saleDate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    /**
     * Updates the status of a sale to CANCELLED.
     * Used within a caller-managed transaction (commit/rollback handled externally).
     */
    public void updateStatus(Connection conn, Long saleId, String status, String cancelledAt, String cancellationReason) throws SQLException {
        String sql = "UPDATE sales SET status = ?, cancelled_at = ?, cancellation_reason = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            if (cancelledAt != null) {
                ps.setString(2, cancelledAt);
            } else {
                ps.setNull(2, java.sql.Types.VARCHAR);
            }
            if (cancellationReason != null) {
                ps.setString(3, cancellationReason);
            } else {
                ps.setNull(3, java.sql.Types.VARCHAR);
            }
            ps.setLong(4, saleId);
            ps.executeUpdate();
        }
    }

    /**
     * Returns only active (non-cancelled) sales sorted by id descending (chronological) with pagination.
     *
     * @param limit  maximum number of results (0 = no limit)
     * @param offset number of results to skip (for pagination)
     */
    public List<Sale> findAllActive(int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM sales WHERE status = 'ACTIVE' ORDER BY id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ?");
        }
        if (offset > 0) {
            sql.append(" OFFSET ?");
        }
        Connection conn = dbManager.getConnection();
        List<Sale> sales = new ArrayList<>();
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
                    sales.add(mapSale(rs));
                }
            }
        }
        return sales;
    }

    /**
     * Returns only active (non-cancelled) sales sorted by id descending (chronological).
     */
    public List<Sale> findAllActive() throws SQLException {
        return findAllActive(0, 0);
    }

    /**
     * Returns sale history (all sales including cancelled) sorted by id descending with pagination.
     *
     * @param limit  maximum number of results (0 = no limit)
     * @param offset number of results to skip (for pagination)
     */
    public List<Sale> findHistory(int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM sales ORDER BY id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ?");
        }
        if (offset > 0) {
            sql.append(" OFFSET ?");
        }
        Connection conn = dbManager.getConnection();
        List<Sale> sales = new ArrayList<>();
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
                    sales.add(mapSale(rs));
                }
            }
        }
        return sales;
    }

    /**
     * Finds a sale by ID using an existing connection (for transactional use).
     */
    public Optional<Sale> findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT * FROM sales WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSale(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Updates the receipt text for a sale.
     * Only sets the receipt if it was not already set (prevents overwrite).
     */
    public void updateReceipt(Connection conn, Long saleId, String receiptText) throws SQLException {
        String sql = "UPDATE sales SET receipt_text = ? WHERE id = ? AND receipt_text IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, receiptText);
            ps.setLong(2, saleId);
            ps.executeUpdate();
        }
    }

    /**
     * Returns all items for a given sale.
     */
    public List<SaleItem> findItemsBySaleId(Long saleId) throws SQLException {
        return findItemsBySaleId(dbManager.getConnection(), saleId);
    }

    /**
     * Returns all items for a given sale using the provided connection
     * (for transactional use, e.g. inside a cancel-sale transaction).
     */
    public List<SaleItem> findItemsBySaleId(Connection conn, Long saleId) throws SQLException {
        String sql = "SELECT * FROM sale_items WHERE sale_id = ?";
        List<SaleItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapItem(rs));
                }
            }
        }
        return items;
    }

    /**
     * Maximum ids bound per IN (...) query. sqlite-jdbc caps bind parameters per
     * statement (250000 in the bundled build), and all-time reports can pass every
     * sale id, so ids are queried in chunks well below that cap.
     */
    static final int ITEMS_BY_SALE_IDS_CHUNK_SIZE = 500;

    /**
     * Returns all items for multiple sales, querying the ids in chunks of
     * {@link #ITEMS_BY_SALE_IDS_CHUNK_SIZE}.
     * Used to avoid a per-sale round trip when building reports.
     */
    public List<SaleItem> findItemsBySaleIds(List<Long> saleIds) throws SQLException {
        if (saleIds == null || saleIds.isEmpty()) {
            return new ArrayList<>();
        }
        Connection conn = dbManager.getConnection();
        List<SaleItem> items = new ArrayList<>();
        for (int from = 0; from < saleIds.size(); from += ITEMS_BY_SALE_IDS_CHUNK_SIZE) {
            List<Long> chunk = saleIds.subList(from, Math.min(from + ITEMS_BY_SALE_IDS_CHUNK_SIZE, saleIds.size()));
            String placeholders = chunk.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            String sql = "SELECT * FROM sale_items WHERE sale_id IN (" + placeholders + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < chunk.size(); i++) {
                    ps.setLong(i + 1, chunk.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapItem(rs));
                    }
                }
            }
        }
        return items;
    }

    /**
     * Returns the most recent active sales, up to {@code limit}, ordered by id
     * descending. Used by the dashboard home view.
     */
    public List<Sale> findRecentActive(int limit) throws SQLException {
        String sql = "SELECT * FROM sales WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT ?";
        Connection conn = dbManager.getConnection();
        List<Sale> sales = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapSale(rs));
                }
            }
        }
        return sales;
    }

    private Sale mapSale(ResultSet rs) throws SQLException {
        Sale sale = new Sale();
        sale.setId(rs.getLong("id"));
        sale.setSaleDate(rs.getString("sale_date"));
        sale.setChannel(rs.getString("channel"));
        sale.setPaymentMethod(rs.getString("payment_method"));
        long customerId = rs.getLong("customer_id");
        if (!rs.wasNull()) {
            sale.setCustomerId(customerId);
        }
        sale.setDiscount(rs.getDouble("discount"));
        sale.setDiscountType(rs.getString("discount_type"));
        sale.setTotalAmount(rs.getDouble("total_amount"));
        sale.setCreatedAt(rs.getString("created_at"));
        try {
            sale.setStatus(rs.getString("status"));
        } catch (SQLException e) {
            sale.setStatus("ACTIVE");
        }
        try {
            sale.setCancelledAt(rs.getString("cancelled_at"));
        } catch (SQLException e) {
            // Column may not exist yet during migration
        }
        try {
            sale.setCancellationReason(rs.getString("cancellation_reason"));
        } catch (SQLException e) {
            // Column may not exist yet during migration
        }
        try {
            sale.setReceiptText(rs.getString("receipt_text"));
        } catch (SQLException e) {
            // Column may not exist yet during migration
        }
        return sale;
    }

    private SaleItem mapItem(ResultSet rs) throws SQLException {
        SaleItem item = new SaleItem();
        item.setId(rs.getLong("id"));
        item.setSaleId(rs.getLong("sale_id"));
        item.setProductId(rs.getLong("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitPrice(rs.getDouble("unit_price"));
        item.setDiscount(rs.getDouble("discount"));
        item.setDiscountType(rs.getString("discount_type"));
        item.setSubtotal(rs.getDouble("subtotal"));
        return item;
    }
}
