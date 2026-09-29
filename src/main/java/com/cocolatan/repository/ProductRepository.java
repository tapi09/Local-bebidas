package com.cocolatan.repository;

import com.cocolatan.model.Product;
import com.cocolatan.util.HierarchyLabel;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Product CRUD operations with search capabilities.
 * Uses raw JDBC with PreparedStatement for data access.
 *
 * <p>Reads resolve the category/subcategory names via LEFT JOIN onto the
 * {@link Product} DTO. The legacy {@code category} column is a derived
 * write-only backfill computed from the DTO names at save/update time —
 * it is never a display source.</p>
 */
public class ProductRepository {

    private static final String BASE_SELECT = "SELECT p.*, c.name AS category_name, s.name AS subcategory_name "
            + "FROM products p "
            + "LEFT JOIN categories c ON p.category_id = c.id "
            + "LEFT JOIN subcategories s ON p.subcategory_id = s.id ";

    private final DatabaseManager dbManager;

    public ProductRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Saves a new product and returns its generated ID.
     * Auto-generates SKU from the generated ID.
     * Throws SQLException if barcode is duplicate.
     */
    public Long save(Product product) throws SQLException {
        String sql = "INSERT INTO products (name, category, presentation, cost_price, sale_price, sale_price_pedidosya, supplier_id, barcode, photo_path, min_stock, active, category_id, subcategory_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, product.getName());
            ps.setString(2, resolveLegacyLabel(product));
            ps.setString(3, product.getPresentation());
            ps.setDouble(4, product.getCostPrice());
            ps.setDouble(5, product.getSalePrice());
            ps.setDouble(6, product.getPedidosyaPrice());
            if (product.getSupplierId() != null) {
                ps.setLong(7, product.getSupplierId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setString(8, product.getBarcode());
            ps.setString(9, product.getPhotoPath());
            ps.setInt(10, product.getMinStock());
            ps.setBoolean(11, product.isActive());
            if (product.getCategoryId() != null) {
                ps.setLong(12, product.getCategoryId());
            } else {
                ps.setNull(12, Types.INTEGER);
            }
            if (product.getSubcategoryId() != null) {
                ps.setLong(13, product.getSubcategoryId());
            } else {
                ps.setNull(13, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    product.setId(id);
                    // Auto-generate SKU from the ID
                    String sku = String.format("SKU-%05d", id);
                    try (PreparedStatement updatePs = conn.prepareStatement(
                            "UPDATE products SET sku = ? WHERE id = ?")) {
                        updatePs.setString(1, sku);
                        updatePs.setLong(2, id);
                        updatePs.executeUpdate();
                    }
                    return id;
                }
                throw new SQLException("Failed to retrieve generated product ID");
            }
        }
    }

    /**
     * Finds a product by ID.
     */
    public Optional<Product> findById(Long id) throws SQLException {
        String sql = BASE_SELECT + "WHERE p.id = ?";
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
     * Returns all active products ordered by name.
     */
    public List<Product> findAllActive() throws SQLException {
        String sql = BASE_SELECT + "WHERE p.active = 1 ORDER BY p.name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Product> products = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                products.add(mapRow(rs));
            }
        }
        return products;
    }

    /**
     * Searches products by name, barcode, or SKU (case-insensitive LIKE).
     */
    public List<Product> searchByName(String name) throws SQLException {
        String sql = BASE_SELECT + "WHERE p.active = 1 AND (UPPER(p.name) LIKE UPPER(?) OR p.barcode = ? COLLATE NOCASE OR UPPER(p.sku) LIKE UPPER(?)) ORDER BY p.name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Product> products = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + name + "%";
            ps.setString(1, pattern);
            ps.setString(2, name);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapRow(rs));
                }
            }
        }
        return products;
    }

    /**
     * Searches active products by exact barcode match (case-insensitive).
     */
    public List<Product> searchByBarcode(String barcode) throws SQLException {
        String sql = BASE_SELECT + "WHERE p.barcode = ? COLLATE NOCASE AND p.active = 1";
        Connection conn = dbManager.getConnection();
        List<Product> products = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, barcode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapRow(rs));
                }
            }
        }
        return products;
    }

    /**
     * Finds a product by exact barcode, regardless of active status.
     * Used for duplicate detection during save/update.
     */
    public Optional<Product> findByBarcodeExact(String barcode) throws SQLException {
        String sql = BASE_SELECT + "WHERE p.barcode = ? COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, barcode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Returns products matching the given ids (regardless of active status)
     * using a single batched IN query. Used to resolve product names in bulk.
     */
    public List<Product> findAllByIds(List<Long> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = ids.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = BASE_SELECT + "WHERE p.id IN (" + placeholders + ")";
        Connection conn = dbManager.getConnection();
        List<Product> products = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapRow(rs));
                }
            }
        }
        return products;
    }

    /**
     * Applies a percentage multiplier to the local and/or PedidosYa prices of the
     * given active products in a single batched UPDATE. Prices are rounded to 2
     * decimals. Inactive products and ids outside the list are left untouched.
     *
     * @param productIds          products to update
     * @param applySalePrice      whether to adjust the local sale price
     * @param applyPedidosyaPrice whether to adjust the PedidosYa price
     * @param salePriceMultiplier e.g. 1.05 for +5%, 0.90 for -10%
     * @param pedidosyaMultiplier multiplier for the PedidosYa price
     * @return number of rows updated
     */
    public int bulkUpdatePrices(List<Long> productIds, boolean applySalePrice, boolean applyPedidosyaPrice,
                                double salePriceMultiplier, double pedidosyaMultiplier) throws SQLException {
        if (productIds == null || productIds.isEmpty() || (!applySalePrice && !applyPedidosyaPrice)) {
            return 0;
        }
        String placeholders = productIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        StringBuilder sql = new StringBuilder("UPDATE products SET ");
        if (applySalePrice && applyPedidosyaPrice) {
            sql.append("sale_price = ROUND(sale_price * ?, 2), sale_price_pedidosya = ROUND(sale_price_pedidosya * ?, 2), ");
        } else if (applySalePrice) {
            sql.append("sale_price = ROUND(sale_price * ?, 2), ");
        } else {
            sql.append("sale_price_pedidosya = ROUND(sale_price_pedidosya * ?, 2), ");
        }
        sql.append("updated_at = datetime('now','localtime') WHERE active = 1 AND id IN (").append(placeholders).append(")");
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            if (applySalePrice) {
                ps.setDouble(paramIndex++, salePriceMultiplier);
            }
            if (applyPedidosyaPrice) {
                ps.setDouble(paramIndex++, pedidosyaMultiplier);
            }
            for (Long id : productIds) {
                ps.setLong(paramIndex++, id);
            }
            return ps.executeUpdate();
        }
    }

    /**
     * Returns all active products of a supplier, ordered by name.
     */
    public List<Product> findBySupplierId(Long supplierId) throws SQLException {
        String sql = BASE_SELECT + "WHERE p.active = 1 AND p.supplier_id = ? ORDER BY p.name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Product> products = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapRow(rs));
                }
            }
        }
        return products;
    }

    /**
     * Returns all active products of a category, ordered by name.
     */
    public List<Product> findByCategoryId(Long categoryId) throws SQLException {
        String sql = BASE_SELECT + "WHERE p.active = 1 AND p.category_id = ? ORDER BY p.name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Product> products = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapRow(rs));
                }
            }
        }
        return products;
    }

    /**
     * Returns all active products of a subcategory, ordered by name.
     */
    public List<Product> findBySubcategoryId(Long subcategoryId) throws SQLException {
        String sql = BASE_SELECT + "WHERE p.active = 1 AND p.subcategory_id = ? ORDER BY p.name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Product> products = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, subcategoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapRow(rs));
                }
            }
        }
        return products;
    }

    /**
     * Returns all active products whose legacy category text matches.
     * @deprecated hierarchy filters use ids after the subcategory change;
     * kept for compatibility with existing consumers.
     */
    @Deprecated
    public List<Product> findByCategory(String category) throws SQLException {
        String sql = BASE_SELECT + "WHERE p.active = 1 AND p.category = ? ORDER BY p.name COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<Product> products = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapRow(rs));
                }
            }
        }
        return products;
    }

    /**
     * Returns the distinct non-empty legacy categories of active products,
     * ordered case-insensitively.
     * @deprecated hierarchy filters use ids after the subcategory change;
     * kept for compatibility with existing consumers.
     */
    @Deprecated
    public List<String> findDistinctCategories() throws SQLException {
        String sql = "SELECT DISTINCT category FROM products "
                + "WHERE active = 1 AND category IS NOT NULL AND TRIM(category) <> '' "
                + "ORDER BY category COLLATE NOCASE";
        Connection conn = dbManager.getConnection();
        List<String> categories = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString(1));
            }
        }
        return categories;
    }

    /**
     * Updates an existing product.
     */
    public void update(Product product) throws SQLException {
        String sql = "UPDATE products SET name = ?, category = ?, presentation = ?, cost_price = ?, sale_price = ?, "
                + "sale_price_pedidosya = ?, supplier_id = ?, barcode = ?, photo_path = ?, min_stock = ?, active = ?, category_id = ?, subcategory_id = ?, updated_at = datetime('now','localtime') WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product.getName());
            ps.setString(2, resolveLegacyLabel(product));
            ps.setString(3, product.getPresentation());
            ps.setDouble(4, product.getCostPrice());
            ps.setDouble(5, product.getSalePrice());
            ps.setDouble(6, product.getPedidosyaPrice());
            if (product.getSupplierId() != null) {
                ps.setLong(7, product.getSupplierId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setString(8, product.getBarcode());
            ps.setString(9, product.getPhotoPath());
            ps.setInt(10, product.getMinStock());
            ps.setBoolean(11, product.isActive());
            if (product.getCategoryId() != null) {
                ps.setLong(12, product.getCategoryId());
            } else {
                ps.setNull(12, Types.INTEGER);
            }
            if (product.getSubcategoryId() != null) {
                ps.setLong(13, product.getSubcategoryId());
            } else {
                ps.setNull(13, Types.INTEGER);
            }
            ps.setLong(14, product.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Deactivates a product (soft delete).
     */
    public void deactivate(Long id) throws SQLException {
        String sql = "UPDATE products SET active = 0, updated_at = datetime('now','localtime') WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Checks if a product has purchase or sale history.
     */
    public boolean hasHistory(Long productId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM purchase_items WHERE product_id = ? "
                + "UNION ALL "
                + "SELECT COUNT(*) FROM sale_items WHERE product_id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, productId);
            ps.setLong(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                int total = 0;
                while (rs.next()) {
                    total += rs.getInt(1);
                }
                return total > 0;
            }
        }
    }

    /**
     * Returns the number of active products whose current stock is below their
     * min_stock threshold. Uses denormalized current_stock column for O(1) performance.
     */
    public int countLowStock() throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE active = 1 AND current_stock < min_stock";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Returns the number of active products currently shown as out of stock
     * (current_stock equal to zero). Uses denormalized current_stock column for O(1) performance.
     */
    public int countOutOfStock() throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE active = 1 AND current_stock = 0";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Returns the number of active products. Used by the dashboard.
     */
    public int countActiveProducts() throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE active = 1";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Returns the number of active products with low stock or out of stock
     * (current_stock strictly below the min_stock threshold). Uses denormalized
     * current_stock column for O(1) performance.
     */
    public int countLowOrOutOfStock() throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE active = 1 AND current_stock < min_stock";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Updates the current_stock of a product by a delta (positive or negative).
     * Used to maintain the denormalized stock cache in O(1) within a transaction.
     * Does NOT manage transactions — the caller controls commit/rollback.
     *
     * @param conn      existing database connection (transactional)
     * @param productId the product to update
     * @param delta     the change in stock (negative for sales, positive for purchases/adjustments)
     * @return number of rows updated (0 or 1)
     */
    public int updateStock(Connection conn, Long productId, int delta) throws SQLException {
        String sql = "UPDATE products SET current_stock = current_stock + ?, updated_at = datetime('now','localtime') WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setLong(2, productId);
            return ps.executeUpdate();
        }
    }

    /**
     * Updates the cost price of a product.
     */
    public void updateCostPrice(Long productId, double newCostPrice) throws SQLException {
        Connection conn = dbManager.getConnection();
        updateCostPrice(conn, productId, newCostPrice);
    }

    /**
     * Updates the cost price of a product using an existing connection.
     * Does NOT manage transactions — the caller controls commit/rollback.
     */
    public void updateCostPrice(Connection conn, Long productId, double newCostPrice) throws SQLException {
        String sql = "UPDATE products SET cost_price = ?, updated_at = datetime('now','localtime') WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newCostPrice);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }

    /**
     * Single legacy write point: the {@code category} column is derived from
     * the DTO names via {@link HierarchyLabel}, never read from the entity.
     */
    private String resolveLegacyLabel(Product product) {
        return HierarchyLabel.resolve(product.getCategoryName(), product.getSubcategoryName());
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Product product = new Product();
        product.setId(rs.getLong("id"));
        product.setSku(rs.getString("sku"));
        product.setName(rs.getString("name"));
        product.setCategory(rs.getString("category"));
        product.setPresentation(rs.getString("presentation"));
        product.setCostPrice(rs.getDouble("cost_price"));
        product.setSalePrice(rs.getDouble("sale_price"));
        product.setPedidosyaPrice(rs.getDouble("sale_price_pedidosya"));
        long supplierId = rs.getLong("supplier_id");
        if (!rs.wasNull()) {
            product.setSupplierId(supplierId);
        }
        long categoryId = rs.getLong("category_id");
        if (!rs.wasNull()) {
            product.setCategoryId(categoryId);
        }
        long subcategoryId = rs.getLong("subcategory_id");
        if (!rs.wasNull()) {
            product.setSubcategoryId(subcategoryId);
        }
        product.setCategoryName(rs.getString("category_name"));
        product.setSubcategoryName(rs.getString("subcategory_name"));
        product.setBarcode(rs.getString("barcode"));
        product.setPhotoPath(rs.getString("photo_path"));
        product.setMinStock(rs.getInt("min_stock"));
        product.setCurrentStock(rs.getInt("current_stock"));
        product.setActive(rs.getBoolean("active"));
        product.setCreatedAt(rs.getString("created_at"));
        product.setUpdatedAt(rs.getString("updated_at"));
        return product;
    }
}
