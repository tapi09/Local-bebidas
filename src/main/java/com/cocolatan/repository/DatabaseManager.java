package com.cocolatan.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton manager for SQLite database connection lifecycle.
 * Handles schema initialization, WAL mode, and foreign keys.
 */
public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());

    private static DatabaseManager instance;
    private final Connection connection;
    private final boolean ownsConnection;

    private static final String SCHEMA_SQL = """
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

            CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
            CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode);
            CREATE INDEX IF NOT EXISTS idx_products_active ON products(active);
            CREATE INDEX IF NOT EXISTS idx_purchases_date ON purchases(purchase_date);
            CREATE INDEX IF NOT EXISTS idx_purchases_supplier ON purchases(supplier_id);
            CREATE INDEX IF NOT EXISTS idx_purchase_items_product ON purchase_items(product_id);
            CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(sale_date);
            CREATE INDEX IF NOT EXISTS idx_sales_channel ON sales(channel);
            CREATE INDEX IF NOT EXISTS idx_sale_items_product ON sale_items(product_id);
            CREATE INDEX IF NOT EXISTS idx_stock_movements_product ON stock_movements(product_id);
            CREATE INDEX IF NOT EXISTS idx_stock_movements_type ON stock_movements(movement_type);
            CREATE INDEX IF NOT EXISTS idx_stock_movements_date ON stock_movements(created_at);
            CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name);
            CREATE INDEX IF NOT EXISTS idx_products_barcode_nocase ON products(barcode COLLATE NOCASE);
            CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON sale_items(sale_id);
            CREATE INDEX IF NOT EXISTS idx_purchase_items_purchase ON purchase_items(purchase_id);
            """;

    private DatabaseManager(Connection connection, boolean ownsConnection) {
        this.connection = connection;
        this.ownsConnection = ownsConnection;
    }

    /**
     * Creates an in-memory DatabaseManager for testing.
     */
    public static DatabaseManager createInMemory() {
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
            DatabaseManager mgr = new DatabaseManager(conn, true);
            configureConnection(conn);
            mgr.initSchema();
            return mgr;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create in-memory database", e);
        }
    }

    /**
     * Creates a file-based DatabaseManager for production use.
     *
     * @param dbPath path to the SQLite database file
     */
    public static DatabaseManager createFromFile(String dbPath) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            DatabaseManager mgr = new DatabaseManager(conn, true);
            configureConnection(conn);
            mgr.initSchema();
            return mgr;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open database: " + dbPath, e);
        }
    }

    /**
     * Creates a DatabaseManager wrapping an existing connection (for testing).
     */
    public static DatabaseManager fromConnection(Connection connection) {
        DatabaseManager mgr = new DatabaseManager(connection, false);
        try {
            configureConnection(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to configure connection", e);
        }
        mgr.initSchema();
        return mgr;
    }

    private static void configureConnection(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    /**
     * Initializes the database schema. Idempotent — safe to call multiple times.
     */
    public void initSchema() {
        try (Statement stmt = connection.createStatement()) {
            // Split by semicolons and execute each statement
            String[] statements = SCHEMA_SQL.split(";");
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            // Record schema version if not already present
            try {
                stmt.execute("INSERT INTO schema_version (version) VALUES (1)");
            } catch (SQLException e) {
                // Version already exists — idempotent
            }
            // Migration: add new columns to existing tables (safe to ignore if already exist)
            runMigration(stmt, "ALTER TABLE sales ADD COLUMN customer_id INTEGER");
            runMigration(stmt, "ALTER TABLE sales ADD COLUMN discount REAL NOT NULL DEFAULT 0");
            runMigration(stmt, "ALTER TABLE sales ADD COLUMN discount_type TEXT NOT NULL DEFAULT 'NONE'");
            runMigration(stmt, "ALTER TABLE sale_items ADD COLUMN discount REAL NOT NULL DEFAULT 0");
            runMigration(stmt, "ALTER TABLE sale_items ADD COLUMN discount_type TEXT NOT NULL DEFAULT 'NONE'");
            runMigration(stmt, "ALTER TABLE purchases ADD COLUMN subtotal REAL NOT NULL DEFAULT 0");
            runMigration(stmt, "ALTER TABLE purchases ADD COLUMN tax_amount REAL NOT NULL DEFAULT 0");
            runMigration(stmt, "ALTER TABLE purchases ADD COLUMN created_by TEXT");
            runMigration(stmt, "ALTER TABLE sales ADD COLUMN created_by TEXT");
            // Migration v2: SKU and PedidosYa price
            runMigration(stmt, "ALTER TABLE products ADD COLUMN sku TEXT");
            runMigration(stmt, "ALTER TABLE products ADD COLUMN sale_price_pedidosya REAL DEFAULT 0");
            // Backfill SKU for existing products that don't have one yet
            runMigration(stmt, "UPDATE products SET sku = 'SKU-' || SUBSTR('00000' || CAST(id AS TEXT), -5) WHERE sku IS NULL");
            // Migration v3: product photo path
            runMigration(stmt, "ALTER TABLE products ADD COLUMN photo_path TEXT");
            // Migration v4: sale cancellation support
            runMigration(stmt, "ALTER TABLE sales ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'");
            runMigration(stmt, "ALTER TABLE sales ADD COLUMN cancelled_at TEXT");
            runMigration(stmt, "ALTER TABLE sales ADD COLUMN cancellation_reason TEXT");
            // Migration v5: receipt text storage
            runMigration(stmt, "ALTER TABLE sales ADD COLUMN receipt_text TEXT");
            // Migration v6: product category hierarchy
            runMigration(stmt, "ALTER TABLE products ADD COLUMN category_id INTEGER");
            runMigration(stmt, "ALTER TABLE products ADD COLUMN subcategory_id INTEGER");
            runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category_id)");
            runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_products_subcategory_id ON products(subcategory_id)");
            runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_subcategories_category ON subcategories(category_id)");
            seedHierarchy(stmt);
            backfillHierarchy(stmt);
            // Migration v7: missing indexes for barcode NOCASE lookups and FK joins
            runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_products_barcode_nocase ON products(barcode COLLATE NOCASE)");
             runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON sale_items(sale_id)");
             runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_purchase_items_purchase ON purchase_items(purchase_id)");
             // Migration v8: invoice photo path and register sessions
             runMigration(stmt, "ALTER TABLE purchases ADD COLUMN invoice_photo_path TEXT");
             runMigration(stmt, "CREATE TABLE IF NOT EXISTS register_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, register_name TEXT NOT NULL DEFAULT 'Caja Principal', open_date TEXT NOT NULL DEFAULT (datetime('now','localtime')), close_date TEXT, status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')), initial_cash REAL NOT NULL DEFAULT 0 CHECK (initial_cash >= 0), expected_cash REAL, actual_cash REAL, created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')), closed_at TEXT)");
             runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_register_sessions_status ON register_sessions(status)");
             runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_register_sessions_open_date ON register_sessions(open_date)");
             // Migration v9: purchase payment method
             runMigration(stmt, "ALTER TABLE purchases ADD COLUMN payment_method TEXT");
             // Migration v10: configurable business name
             runMigration(stmt, "INSERT OR IGNORE INTO app_config (key, value) VALUES ('business_name', 'Cocolatán')");
             // Migration v11: force password change on first login
             runMigration(stmt, "ALTER TABLE users ADD COLUMN must_change_password INTEGER NOT NULL DEFAULT 0");
             // Migration v12 note: password recovery master key is intentionally
             // NOT seeded here anymore. Shipping a known default key would let
             // anyone reset a user's password from the login screen. The provider
             // must configure it once through the Users screen (first-time setup
             // accepts an empty current key); recovery only works after that.
// Migration v13: persist dismissed alerts across restarts
              runMigration(stmt, "CREATE TABLE IF NOT EXISTS alert_dismissals (id INTEGER PRIMARY KEY AUTOINCREMENT, alert_type TEXT NOT NULL, product_id INTEGER NOT NULL, lot_number TEXT, dismissed_at TEXT NOT NULL DEFAULT (datetime('now','localtime')), UNIQUE (alert_type, product_id, lot_number))");
              runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_alert_dismissals_lookup ON alert_dismissals(alert_type, product_id)");
              // Migration v14: denormalized current_stock column for O(1) dashboard queries
              runMigration(stmt, "ALTER TABLE products ADD COLUMN current_stock INTEGER NOT NULL DEFAULT 0 CHECK (current_stock >= 0)");
              runMigration(stmt, "CREATE INDEX IF NOT EXISTS idx_products_current_stock ON products(current_stock)");
              // Backfill current_stock from stock_movements history — done row-by-row
              // (not a single aggregate UPDATE) because SQLite aborts the ENTIRE statement
              // if any one row violates the CHECK (current_stock >= 0) constraint (confirmed
              // empirically, audit v3 B5): a single product with a negative net movement
              // history would otherwise silently leave ALL products at the DEFAULT 0.
              backfillCurrentStock(stmt);
              // Migration v15: convert sale_date and purchase_date from dd/MM/yyyy to YYYY-MM-DD (ISO-8601)
              runMigration(stmt, "UPDATE sales SET sale_date = substr(sale_date,7,4) || '-' || substr(sale_date,4,2) || '-' || substr(sale_date,1,2) WHERE sale_date LIKE '__/__/____'");
              runMigration(stmt, "UPDATE purchases SET purchase_date = substr(purchase_date,7,4) || '-' || substr(purchase_date,4,2) || '-' || substr(purchase_date,1,2) WHERE purchase_date LIKE '__/__/____'");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize schema", e);
        }
    }

    private void runMigration(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            // Expected on re-run for additive migrations ("column already exists" etc).
            // Logged (not swallowed silently) so a genuinely unexpected failure is still
            // observable — audit v3 A4: silently ignoring ALL SQLExceptions here is what
            // let the v14 backfill's CHECK-constraint abort (B5) go completely unnoticed.
            LOGGER.log(Level.WARNING, "Migration statement failed (harmless if already applied): " + sql, e);
        }
    }

    /**
     * Backfills products.current_stock (migration v14) one product at a time. A single
     * aggregate UPDATE was tried originally, but SQLite aborts the whole statement if any
     * one row would violate CHECK (current_stock >= 0) — confirmed empirically (audit v3,
     * B5) to leave every product at the DEFAULT 0 when just one had a negative net stock
     * history. Negative nets are clamped to 0 and logged for manual review instead of
     * blocking the backfill for every other product.
     */
    private void backfillCurrentStock(Statement stmt) {
        Map<Long, Integer> nets = new LinkedHashMap<>();
        try (ResultSet rs = stmt.executeQuery(
                "SELECT p.id AS id, "
                + "COALESCE(SUM(CASE WHEN m.movement_type IN ('ENTRY','ADJUSTMENT') THEN m.quantity ELSE 0 END), 0) "
                + "- COALESCE(SUM(CASE WHEN m.movement_type = 'EXIT' THEN m.quantity ELSE 0 END), 0) AS net "
                + "FROM products p LEFT JOIN stock_movements m ON m.product_id = p.id GROUP BY p.id")) {
            while (rs.next()) {
                nets.put(rs.getLong("id"), rs.getInt("net"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "current_stock backfill: failed to compute net stock per product", e);
            return;
        }

        try (PreparedStatement ps = stmt.getConnection().prepareStatement(
                "UPDATE products SET current_stock = ? WHERE id = ?")) {
            for (Map.Entry<Long, Integer> entry : nets.entrySet()) {
                int net = entry.getValue();
                if (net < 0) {
                    LOGGER.log(Level.WARNING, "current_stock backfill: product id=" + entry.getKey()
                            + " has a negative net stock history (" + net + "); clamping to 0. "
                            + "This indicates pre-existing inconsistent stock data that needs manual review.");
                    net = 0;
                }
                ps.setInt(1, net);
                ps.setLong(2, entry.getKey());
                try {
                    ps.executeUpdate();
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "current_stock backfill: failed for product id=" + entry.getKey(), e);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "current_stock backfill: failed to prepare update statement", e);
        }
    }

    /**
     * Seeds the local Argentine category hierarchy. Idempotent via INSERT OR IGNORE:
     * category names are UNIQUE and subcategories carry UNIQUE (category_id, name).
     */
    private void seedHierarchy(Statement stmt) {
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Cervezas', 1, 1)");
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Gaseosas', 2, 1)");
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Aguas', 3, 1)");
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Vinos', 4, 1)");
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Aperitivos', 5, 1)");
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Whiskys', 6, 1)");
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Destilados', 7, 1)");
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Energéticas', 8, 1)");
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Jugos', 9, 1)");
        runMigration(stmt, "INSERT OR IGNORE INTO categories (name, sort_order, active) VALUES ('Sidras', 10, 1)");
        seedSubcategories("Cervezas");
        seedSubcategories("Gaseosas");
    }

    /**
     * Seeds the standard subcategory names for a category. Idempotent via INSERT OR IGNORE
     * (subcategories carry UNIQUE (category_id, name)). Uses a PreparedStatement so the
     * category name is bound instead of concatenated — the seed data is static today, but
     * the parametrized form removes the unsafe SQL-assembly pattern from the codebase.
     */
    private void seedSubcategories(String categoryName) {
        seedSubcategory(categoryName, "Latas", 1);
        seedSubcategory(categoryName, "Botella", 2);
        seedSubcategory(categoryName, "Retornable", 3);
        seedSubcategory(categoryName, "Descartable", 4);
    }

    private void seedSubcategory(String categoryName, String subName, int sortOrder) {
        String sql = "INSERT OR IGNORE INTO subcategories (category_id, name, sort_order, active) "
                + "SELECT id, ?, ?, 1 FROM categories WHERE name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, subName);
            ps.setInt(2, sortOrder);
            ps.setString(3, categoryName);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Idempotent seed — a failure here is harmless (category/subcategory already
            // present, or a concurrent init). Mirrors the tolerant behavior of runMigration.
        }
    }

    /**
     * Best-effort backfill of legacy free-text categories onto the new hierarchy ids.
     * Matches NOCASE + trim; honors the composite "Category — Subcategory" format.
     * Unmatched rows stay NULL and the legacy text is never overwritten.
     */
    private void backfillHierarchy(Statement stmt) {
        runMigration(stmt, "UPDATE products SET category_id = ("
                + "SELECT c.id FROM categories c "
                + "WHERE LOWER(TRIM(c.name)) = LOWER(TRIM("
                + "SUBSTR(products.category, 1, INSTR(products.category || ' — ', ' — ') - 1)"
                + "))"
                + ") WHERE TRIM(products.category) <> ''");
        runMigration(stmt, "UPDATE products SET subcategory_id = ("
                + "SELECT s.id FROM subcategories s "
                + "WHERE s.category_id = products.category_id "
                + "AND LOWER(TRIM(s.name)) = LOWER(TRIM("
                + "SUBSTR(products.category, INSTR(products.category, ' — ') + 3)"
                + "))"
                + ") WHERE TRIM(products.category) <> '' "
                + "AND products.category_id IS NOT NULL "
                + "AND products.category LIKE '% — %'");
    }

    /**
     * Returns the active database connection.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Closes the database connection if this manager owns it.
     */
    public void close() {
        if (ownsConnection) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to close database connection", e);
            }
        }
    }
}
