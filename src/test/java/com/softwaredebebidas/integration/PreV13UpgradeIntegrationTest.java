package com.softwaredebebidas.integration;

import com.softwaredebebidas.repository.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulates "install the new .exe over my real existing database" — never against any
 * real user database, always a synthetic file built from scratch in a @TempDir. Seeds a
 * database exactly as it would look BEFORE migrations v13/v14/v15 existed (base schema +
 * v1-v12 columns only, dates stored dd/MM/yyyy, no current_stock column), inserts data
 * directly via raw JDBC (bypassing repositories, which only know the CURRENT format), then
 * opens that same file with the real DatabaseManager.createFromFile() — which is exactly
 * what happens when the updated app starts against an existing installation — and checks
 * that no rows are lost and the new columns/formats come out correct.
 *
 * The base CREATE TABLE text in DatabaseManager already inlines several fields that used
 * to be separate ALTER-based migrations (sales.customer_id/discount/discount_type/
 * created_by/receipt_text, purchases.subtotal/tax_amount/created_by, sale_items.discount/
 * discount_type) — those are NOT re-applied here, only the columns still missing from that
 * base text (sku/sale_price_pedidosya/photo_path/category_id/subcategory_id on products;
 * status/cancelled_at/cancellation_reason on sales; invoice_photo_path/payment_method on
 * purchases) are added, mirroring exactly what a real pre-v13 installation would already
 * have from having run v1-v12 previously.
 */
class PreV13UpgradeIntegrationTest {

    // Copied verbatim from DatabaseManager.SCHEMA_SQL (private, not accessible from test
    // code) — this is intentionally the SAME literal the production code uses for a fresh
    // install, so this test seeds a schema shape DatabaseManager itself considers "base".
    private static final String BASE_SCHEMA_SQL = """
            CREATE TABLE IF NOT EXISTS suppliers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                contact TEXT,
                phone TEXT,
                email TEXT,
                address TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            );

            CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                sort_order INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            );

            CREATE TABLE IF NOT EXISTS subcategories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                UNIQUE (category_id, name),
                FOREIGN KEY (category_id) REFERENCES categories(id)
            );

            CREATE TABLE IF NOT EXISTS products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                presentation TEXT NOT NULL,
                cost_price REAL NOT NULL CHECK (cost_price >= 0),
                sale_price REAL NOT NULL CHECK (sale_price >= 0),
                supplier_id INTEGER,
                barcode TEXT UNIQUE,
                min_stock INTEGER NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
                active INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
            );

            CREATE TABLE IF NOT EXISTS purchases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                supplier_id INTEGER NOT NULL,
                invoice_ref TEXT,
                purchase_date TEXT NOT NULL,
                subtotal REAL NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
                tax_amount REAL NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
                total_amount REAL NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
                notes TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
            );

            CREATE TABLE IF NOT EXISTS purchase_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                purchase_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL CHECK (quantity > 0),
                unit_cost REAL NOT NULL CHECK (unit_cost >= 0),
                lot_number TEXT,
                expiry_date TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE,
                FOREIGN KEY (product_id) REFERENCES products(id)
            );

            CREATE TABLE IF NOT EXISTS sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_date TEXT NOT NULL,
                channel TEXT NOT NULL CHECK (channel IN ('IN','PEDIDOSYA')),
                payment_method TEXT NOT NULL CHECK (payment_method IN ('CASH','CREDIT_CARD','DEBIT_CARD','TRANSFER','MIXED')),
                customer_id INTEGER,
                discount REAL NOT NULL DEFAULT 0,
                discount_type TEXT NOT NULL DEFAULT 'NONE' CHECK (discount_type IN ('NONE','PERCENTAGE','FIXED')),
                total_amount REAL NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
                created_by TEXT,
                receipt_text TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            );

            CREATE TABLE IF NOT EXISTS sale_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL CHECK (quantity > 0),
                unit_price REAL NOT NULL CHECK (unit_price >= 0),
                discount REAL NOT NULL DEFAULT 0,
                discount_type TEXT NOT NULL DEFAULT 'NONE' CHECK (discount_type IN ('NONE','PERCENTAGE','FIXED')),
                subtotal REAL NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
                FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
                FOREIGN KEY (product_id) REFERENCES products(id)
            );

            CREATE TABLE IF NOT EXISTS stock_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                movement_type TEXT NOT NULL CHECK (movement_type IN ('ENTRY','EXIT','ADJUSTMENT')),
                quantity INTEGER NOT NULL CHECK (quantity > 0),
                reference_type TEXT CHECK (reference_type IN ('PURCHASE','SALE','ADJUSTMENT')),
                reference_id INTEGER,
                notes TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                FOREIGN KEY (product_id) REFERENCES products(id)
            );

            CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT,
                email TEXT,
                address TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            );

            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL CHECK (role IN ('ADMIN','CAJERO')),
                display_name TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            );

            CREATE TABLE IF NOT EXISTS app_config (
                key TEXT PRIMARY KEY,
                value TEXT
            );

            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY,
                applied_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            );
            """;

    private static final String[] PRE_V13_ALTERS = {
            // v2/v3/v6 columns not already inlined in BASE_SCHEMA_SQL above
            "ALTER TABLE products ADD COLUMN sku TEXT",
            "ALTER TABLE products ADD COLUMN sale_price_pedidosya REAL DEFAULT 0",
            "ALTER TABLE products ADD COLUMN photo_path TEXT",
            "ALTER TABLE products ADD COLUMN category_id INTEGER",
            "ALTER TABLE products ADD COLUMN subcategory_id INTEGER",
            // v4
            "ALTER TABLE sales ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'",
            "ALTER TABLE sales ADD COLUMN cancelled_at TEXT",
            "ALTER TABLE sales ADD COLUMN cancellation_reason TEXT",
            // v8/v9
            "ALTER TABLE purchases ADD COLUMN invoice_photo_path TEXT",
            "ALTER TABLE purchases ADD COLUMN payment_method TEXT",
    };

    private void seedPreV13Schema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            for (String sql : BASE_SCHEMA_SQL.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            for (String alter : PRE_V13_ALTERS) {
                stmt.execute(alter);
            }
        }
    }

    @Test
    void upgradeOverRealisticPreV13DataPreservesEverythingAndMigratesCorrectly(@TempDir Path tempDir) throws SQLException {
        String dbPath = tempDir.resolve("pre-v13-realistic.db").toString();

        // --- Build the "before" database: pre-v13 schema, legacy dd/MM/yyyy dates, no current_stock ---
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            seedPreV13Schema(conn);

            long supplierId = insertSupplier(conn, "Distribuidora Cuyo");

            long productA = insertProduct(conn, "Cerveza Quilmes 1L", "Cervezas", "Botella", 300.0, 600.0);
            long productB = insertProduct(conn, "Coca-Cola 2.25L", "Gaseosas", "Botella", 500.0, 1200.0);
            long productC = insertProduct(conn, "Fernet Branca 750ml", "Aperitivos", "Botella", 4000.0, 8500.0);

            // Sales with legacy dd/MM/yyyy dates
            long sale1 = insertSale(conn, "01/09/2026", "IN", "CASH", 3600.0);
            insertSaleItem(conn, sale1, productA, 6, 600.0, 3600.0);

            long sale2 = insertSale(conn, "10/09/2026", "PEDIDOSYA", "TRANSFER", 2400.0);
            insertSaleItem(conn, sale2, productB, 2, 1200.0, 2400.0);

            long sale3 = insertSale(conn, "13/09/2026", "IN", "MIXED", 8500.0);
            insertSaleItem(conn, sale3, productC, 1, 8500.0, 8500.0);

            // Purchases with legacy dd/MM/yyyy dates
            long purchase1 = insertPurchase(conn, supplierId, "05/09/2026", 15000.0);
            insertPurchaseItem(conn, purchase1, productA, 50, 300.0);

            long purchase2 = insertPurchase(conn, supplierId, "08/09/2026", 25000.0);
            insertPurchaseItem(conn, purchase2, productB, 50, 500.0);

            // Stock movements: known nets. A: +50 (purchase) -6 (sale) = 44.
            // B: +50 (purchase) -2 (sale) = 48. C: +20 (manual entry) -1 (sale) = 19.
            insertStockMovement(conn, productA, "ENTRY", 50, "PURCHASE", purchase1);
            insertStockMovement(conn, productA, "EXIT", 6, "SALE", sale1);
            insertStockMovement(conn, productB, "ENTRY", 50, "PURCHASE", purchase2);
            insertStockMovement(conn, productB, "EXIT", 2, "SALE", sale2);
            insertStockMovement(conn, productC, "ENTRY", 20, "ADJUSTMENT", null);
            insertStockMovement(conn, productC, "EXIT", 1, "SALE", sale3);
        }

        // --- Capture "before" state independently of the app code ---
        long beforeProductCount = countRows(dbPath, "products");
        long beforeSaleCount = countRows(dbPath, "sales");
        long beforeSaleItemCount = countRows(dbPath, "sale_items");
        long beforePurchaseCount = countRows(dbPath, "purchases");
        long beforePurchaseItemCount = countRows(dbPath, "purchase_items");
        long beforeStockMovementCount = countRows(dbPath, "stock_movements");
        double beforeTotalSalesAmount = sumColumn(dbPath, "sales", "total_amount");
        double beforeTotalPurchaseAmount = sumColumn(dbPath, "purchases", "total_amount");

        // --- Simulate installing the new .exe: open the SAME file with the real app code ---
        DatabaseManager upgraded = DatabaseManager.createFromFile(dbPath);

        // --- Row counts: nothing lost ---
        assertThat(countRows(dbPath, "products")).isEqualTo(beforeProductCount);
        assertThat(countRows(dbPath, "sales")).isEqualTo(beforeSaleCount);
        assertThat(countRows(dbPath, "sale_items")).isEqualTo(beforeSaleItemCount);
        assertThat(countRows(dbPath, "purchases")).isEqualTo(beforePurchaseCount);
        assertThat(countRows(dbPath, "purchase_items")).isEqualTo(beforePurchaseItemCount);
        assertThat(countRows(dbPath, "stock_movements")).isEqualTo(beforeStockMovementCount);

        // --- Money: not a single cent moved ---
        assertThat(sumColumn(dbPath, "sales", "total_amount")).isEqualTo(beforeTotalSalesAmount);
        assertThat(sumColumn(dbPath, "purchases", "total_amount")).isEqualTo(beforeTotalPurchaseAmount);

        // --- Dates: every sale_date/purchase_date now ISO ---
        Connection upgradedConn = upgraded.getConnection();
        try (Statement stmt = upgradedConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT sale_date FROM sales")) {
            while (rs.next()) {
                assertThat(rs.getString("sale_date")).matches("\\d{4}-\\d{2}-\\d{2}");
            }
        }
        try (Statement stmt = upgradedConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT purchase_date FROM purchases")) {
            while (rs.next()) {
                assertThat(rs.getString("purchase_date")).matches("\\d{4}-\\d{2}-\\d{2}");
            }
        }

        // --- current_stock backfilled correctly for every product from real stock_movements math ---
        try (Statement stmt = upgradedConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT p.id, p.current_stock AS stored, ("
                             + "SELECT COALESCE(SUM(CASE WHEN m.movement_type IN ('ENTRY','ADJUSTMENT') THEN m.quantity ELSE 0 END), 0) "
                             + "- COALESCE(SUM(CASE WHEN m.movement_type = 'EXIT' THEN m.quantity ELSE 0 END), 0) "
                             + "FROM stock_movements m WHERE m.product_id = p.id) AS computed "
                             + "FROM products p")) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                assertThat(rs.getInt("stored"))
                        .as("product id=%d current_stock must match recomputed stock_movements net", rs.getLong("id"))
                        .isEqualTo(rs.getInt("computed"));
            }
            assertThat(rows).isEqualTo(3);
        }

        upgraded.close();
    }

    /**
     * FIXED (audit v3, B5): the v14 backfill used to be a single aggregate UPDATE with a
     * subquery; in SQLite, if that UPDATE would set current_stock negative for even ONE row,
     * the ENTIRE statement aborted (no partial application across rows on a CHECK violation),
     * and because runMigration() swallowed the resulting SQLException, EVERY product silently
     * kept current_stock at its DEFAULT 0 — not just the one with a negative net.
     *
     * DatabaseManager.backfillCurrentStock() now applies the same net-stock formula row by
     * row: a healthy product gets its correct computed net, and a product with a negative net
     * (pre-existing inconsistent data) is clamped to 0 and logged for manual review — instead
     * of poisoning every other product's backfill. This test proves it with real production
     * code (DatabaseManager.createFromFile), not just a hypothetical read of the SQL.
     */
    @Test
    void migrationV14BackfillsHealthyProductsEvenWhenAnotherHasNegativeNetStock(@TempDir Path tempDir) throws SQLException {
        String dbPath = tempDir.resolve("pre-v13-negative-stock.db").toString();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            seedPreV13Schema(conn);

            long healthyProduct = insertProduct(conn, "Producto Sano", "Gaseosas", "Botella", 100.0, 200.0);
            long corruptedProduct = insertProduct(conn, "Producto Con Historial Negativo", "Gaseosas", "Botella", 100.0, 200.0);

            // Healthy: +30 entry, -10 exit = net 20 (non-negative, would migrate fine alone).
            insertStockMovement(conn, healthyProduct, "ENTRY", 30, "ADJUSTMENT", null);
            insertStockMovement(conn, healthyProduct, "EXIT", 10, "SALE", null);

            // Corrupted: -15 exit with only a +10 entry recorded = net -5 (possible today because
            // InventoryService.adjustStock() does not keep current_stock in sync, and stock
            // validation bugs elsewhere could already have let an over-sell happen historically).
            insertStockMovement(conn, corruptedProduct, "ENTRY", 10, "ADJUSTMENT", null);
            insertStockMovement(conn, corruptedProduct, "EXIT", 15, "SALE", null);
        }

        DatabaseManager upgraded = DatabaseManager.createFromFile(dbPath);

        try (Connection conn = upgraded.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, current_stock FROM products ORDER BY id")) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                if ("Producto Sano".equals(rs.getString("name"))) {
                    // FIXED: the healthy product's real net (+20) is no longer poisoned by
                    // the other product's negative history.
                    assertThat(rs.getInt("current_stock"))
                            .as("healthy product must get its correctly computed net stock")
                            .isEqualTo(20);
                } else {
                    // The product with an inconsistent (negative) history is clamped to 0
                    // instead of aborting the whole backfill — it still needs manual review,
                    // but it no longer takes every other product down with it.
                    assertThat(rs.getInt("current_stock"))
                            .as("product with negative net stock history must be clamped to 0, not negative")
                            .isEqualTo(0);
                }
            }
            assertThat(rows).isEqualTo(2);
        }

        upgraded.close();
    }

    // --- raw JDBC helpers, deliberately bypassing repositories (which assume the current schema) ---

    private long insertSupplier(Connection conn, String name) throws SQLException {
        try (var ps = conn.prepareStatement("INSERT INTO suppliers (name) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertProduct(Connection conn, String name, String category, String presentation,
                                double costPrice, double salePrice) throws SQLException {
        try (var ps = conn.prepareStatement(
                "INSERT INTO products (name, category, presentation, cost_price, sale_price) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setString(3, presentation);
            ps.setDouble(4, costPrice);
            ps.setDouble(5, salePrice);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertSale(Connection conn, String legacyDate, String channel, String paymentMethod,
                             double totalAmount) throws SQLException {
        try (var ps = conn.prepareStatement(
                "INSERT INTO sales (sale_date, channel, payment_method, total_amount) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, legacyDate);
            ps.setString(2, channel);
            ps.setString(3, paymentMethod);
            ps.setDouble(4, totalAmount);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void insertSaleItem(Connection conn, long saleId, long productId, int quantity,
                                 double unitPrice, double subtotal) throws SQLException {
        try (var ps = conn.prepareStatement(
                "INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal) VALUES (?,?,?,?,?)")) {
            ps.setLong(1, saleId);
            ps.setLong(2, productId);
            ps.setInt(3, quantity);
            ps.setDouble(4, unitPrice);
            ps.setDouble(5, subtotal);
            ps.executeUpdate();
        }
    }

    private long insertPurchase(Connection conn, long supplierId, String legacyDate, double totalAmount)
            throws SQLException {
        try (var ps = conn.prepareStatement(
                "INSERT INTO purchases (supplier_id, purchase_date, total_amount) VALUES (?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, supplierId);
            ps.setString(2, legacyDate);
            ps.setDouble(3, totalAmount);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void insertPurchaseItem(Connection conn, long purchaseId, long productId, int quantity,
                                     double unitCost) throws SQLException {
        try (var ps = conn.prepareStatement(
                "INSERT INTO purchase_items (purchase_id, product_id, quantity, unit_cost) VALUES (?,?,?,?)")) {
            ps.setLong(1, purchaseId);
            ps.setLong(2, productId);
            ps.setInt(3, quantity);
            ps.setDouble(4, unitCost);
            ps.executeUpdate();
        }
    }

    private void insertStockMovement(Connection conn, long productId, String movementType, int quantity,
                                      String referenceType, Long referenceId) throws SQLException {
        try (var ps = conn.prepareStatement(
                "INSERT INTO stock_movements (product_id, movement_type, quantity, reference_type, reference_id) "
                        + "VALUES (?,?,?,?,?)")) {
            ps.setLong(1, productId);
            ps.setString(2, movementType);
            ps.setInt(3, quantity);
            ps.setString(4, referenceType);
            if (referenceId != null) {
                ps.setLong(5, referenceId);
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    private long countRows(String dbPath, String table) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private double sumColumn(String dbPath, String table, String column) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(" + column + "), 0) FROM " + table)) {
            rs.next();
            return rs.getDouble(1);
        }
    }
}
