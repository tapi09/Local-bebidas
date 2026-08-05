package com.cocolatan.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.sql.DriverManager;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseManagerTest {

    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
    }

    @AfterEach
    void tearDown() throws SQLException {
        dbManager.close();
    }

    @Test
    void inMemoryConnectionIsValid() throws SQLException {
        Connection conn = dbManager.getConnection();
        assertThat(conn).isNotNull();
        assertThat(conn.isValid(1)).isTrue();
    }

    @Test
    void schemaCreatesAllSevenTables() throws SQLException {
        Set<String> tables = getTableNames();
        assertThat(tables).contains(
                "suppliers", "products", "purchases", "purchase_items",
                "sales", "sale_items", "stock_movements"
        );
    }

    @Test
    void schemaCreatesProductsIndexes() throws SQLException {
        Set<String> indexes = getIndexNames();
        assertThat(indexes).contains(
                "idx_products_category",
                "idx_products_barcode",
                "idx_products_active"
        );
    }

    @Test
    void schemaCreatesSchemaVersionTable() throws SQLException {
        Set<String> tables = getTableNames();
        assertThat(tables).contains("schema_version");
    }

    @Test
    void schemaVersionIsRecorded() throws SQLException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("version")).isEqualTo(1);
        }
    }

    @Test
    void initSchemaIsIdempotent() throws SQLException {
        // Running initSchema twice should not throw
        dbManager.initSchema();
        dbManager.initSchema();
        // Verify tables still exist
        assertThat(getTableNames()).contains("products");
    }

    @Test
    void walModeIsEnabled() throws SQLException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
            assertThat(rs.next()).isTrue();
            String journalMode = rs.getString("journal_mode");
            // In-memory uses "memory", file-based uses "wal"
            assertThat(journalMode).isIn("wal", "memory");
        }
    }

    @Test
    void foreignKeysAreEnabled() throws SQLException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("foreign_keys")).isEqualTo(1);
        }
    }

    private Set<String> getTableNames() throws SQLException {
        Set<String> tables = new HashSet<>();
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }
        return tables;
    }

    @Test
    void schemaIncludesCreatedByColumnOnPurchases() throws SQLException {
        Connection conn = dbManager.getConnection();
        Set<String> columns = getColumnNames(conn, "purchases");
        assertThat(columns).contains("created_by");
    }

    @Test
    void schemaIncludesCreatedByColumnOnSales() throws SQLException {
        Connection conn = dbManager.getConnection();
        Set<String> columns = getColumnNames(conn, "sales");
        assertThat(columns).contains("created_by");
    }

    @Test
    void schemaCreatesAppConfigTable() throws SQLException {
        Set<String> tables = getTableNames();
        assertThat(tables).contains("app_config");
    }

    @Test
    void appConfigTableHasCorrectColumns() throws SQLException {
        Connection conn = dbManager.getConnection();
        Set<String> columns = getColumnNames(conn, "app_config");
        assertThat(columns).contains("key", "value");
    }

    private Set<String> getColumnNames(Connection conn, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private Set<String> getIndexNames() throws SQLException {
        Set<String> indexes = new HashSet<>();
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%'")) {
            while (rs.next()) {
                indexes.add(rs.getString("name"));
            }
        }
        return indexes;
    }

    @Test
    @DisplayName("getConnection() retorna conexión abierta con WAL mode")
    void getConnectionReturnsOpenConnection() throws SQLException {
        Connection conn = dbManager.getConnection();
        assertThat(conn).isNotNull();
        assertThat(conn.isClosed()).isFalse();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("journal_mode")).isIn("wal", "memory");
        }
    }

    @Test
    @DisplayName("close() libera recursos y cierra la conexión")
    void closeClosesConnection() throws SQLException {
        Connection conn = dbManager.getConnection();
        assertThat(conn.isClosed()).isFalse();
        dbManager.close();
        assertThat(conn.isClosed()).isTrue();
    }

    @Test
    @DisplayName("createFromFile() crea instancia desde archivo .db con WAL mode")
    void createFromFileWorks() throws Exception {
        java.nio.file.Path tempFile = Files.createTempFile("cocolatan-test-", ".db");
        try {
            DatabaseManager fileDb = DatabaseManager.createFromFile(tempFile.toString());
            assertThat(fileDb.getConnection()).isNotNull();
            assertThat(fileDb.getConnection().isValid(1)).isTrue();
            Set<String> tables = new HashSet<>();
            DatabaseMetaData meta = fileDb.getConnection().getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
            assertThat(tables).contains("products", "customers", "schema_version");
            try (Statement stmt = fileDb.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("journal_mode")).isEqualTo("wal");
            }
            fileDb.close();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("fromConnection() envuelve una conexión existente")
    void wrapsExistingConnectionViaFromConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        DatabaseManager customDb = DatabaseManager.fromConnection(conn);
        assertThat(customDb.getConnection()).isSameAs(conn);
        assertThat(customDb.getConnection().isValid(1)).isTrue();
        Set<String> tables = new HashSet<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }
        assertThat(tables).contains("products", "customers");
        customDb.close();
        assertThat(conn.isClosed()).isFalse();
        conn.close();
    }

    @Test
    void schemaCreatesCategoryAndSubcategoryTables() throws SQLException {
        Set<String> tables = getTableNames();
        assertThat(tables).contains("categories", "subcategories");
    }

    @Test
    void productsGainNullableCategoryAndSubcategoryColumns() throws SQLException {
        Connection conn = dbManager.getConnection();
        Set<String> columns = getColumnNames(conn, "products");
        assertThat(columns).contains("category_id", "subcategory_id", "category");
    }

    @Test
    void productCategoryAndSubcategoryColumnsAreNullable() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();
        Map<String, Boolean> nullable = new HashMap<>();
        try (ResultSet rs = meta.getColumns(null, null, "products", "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME").toLowerCase();
                if (columnName.equals("category_id") || columnName.equals("subcategory_id")) {
                    nullable.put(columnName, rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                }
            }
        }
        assertThat(nullable).containsEntry("category_id", true).containsEntry("subcategory_id", true);
    }

    @Test
    void seedPopulatesHierarchyOnce() throws SQLException {
        assertThat(countRows("categories")).isEqualTo(10);
        assertThat(countRows("subcategories")).isEqualTo(8);
        dbManager.initSchema();
        assertThat(countRows("categories")).isEqualTo(10);
        assertThat(countRows("subcategories")).isEqualTo(8);
    }

    @Test
    void migrationReRunIsNoOp() throws SQLException {
        dbManager.initSchema();
        dbManager.initSchema();
        assertThat(getTableNames()).contains("categories", "subcategories");
        assertThat(countRows("categories")).isEqualTo(10);
        assertThat(countRows("subcategories")).isEqualTo(8);
    }

    @Test
    void schemaVersionRemainsOneAfterMigrationReRuns() throws SQLException {
        dbManager.initSchema();
        dbManager.initSchema();
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("version")).isEqualTo(1);
        }
    }

    @Test
    void seedOrdersCategoriesBySortOrder() throws SQLException {
        List<String> ordered = new ArrayList<>();
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM categories ORDER BY sort_order, name COLLATE NOCASE")) {
            while (rs.next()) {
                ordered.add(rs.getString("name"));
            }
        }
        assertThat(ordered).containsExactly(
                "Cervezas", "Gaseosas", "Aguas", "Vinos", "Aperitivos",
                "Whiskys", "Destilados", "Energéticas", "Jugos", "Sidras");
    }

    @Test
    void seedCreatesSubcategoriesForCervezasAndGaseosas() throws SQLException {
        Set<String> expected = new HashSet<>(List.of("Latas", "Botella", "Retornable", "Descartable"));
        assertThat(getSubcategoryNames(getCategoryIdByName("Cervezas"))).isEqualTo(expected);
        assertThat(getSubcategoryNames(getCategoryIdByName("Gaseosas"))).isEqualTo(expected);
    }

    @Test
    @DisplayName("re-seed restaura subcategorías borradas — seed idempotente vía PreparedStatement")
    void reseedRestoresDeletedSubcategories() throws SQLException {
        try (Statement stmt = dbManager.getConnection().createStatement()) {
            stmt.execute("DELETE FROM subcategories WHERE category_id = (SELECT id FROM categories WHERE name = 'Cervezas')");
        }
        assertThat(getSubcategoryNames(getCategoryIdByName("Cervezas"))).isEmpty();

        dbManager.initSchema();

        Set<String> expected = new HashSet<>(List.of("Latas", "Botella", "Retornable", "Descartable"));
        assertThat(getSubcategoryNames(getCategoryIdByName("Cervezas"))).isEqualTo(expected);
        assertThat(countRows("subcategories")).isEqualTo(8);
    }

    @Test
    void backfillMapsKnownCategoryNameCaseInsensitive() throws SQLException {
        long productId = insertProduct("Cerveza en lata", "cervezas");
        dbManager.initSchema();
        assertThat(getProductLong(productId, "category_id")).isEqualTo(getCategoryIdByName("Cervezas"));
    }

    @Test
    void backfillMapsTrimmedCategoryName() throws SQLException {
        long productId = insertProduct("Agua mineral", "  aguas  ");
        dbManager.initSchema();
        assertThat(getProductLong(productId, "category_id")).isEqualTo(getCategoryIdByName("Aguas"));
    }

    @Test
    void backfillMapsCompositeCategoryAndSubcategory() throws SQLException {
        long productId = insertProduct("Cerveza", "Cervezas — Latas");
        dbManager.initSchema();
        long cervezasId = getCategoryIdByName("Cervezas");
        assertThat(getProductLong(productId, "category_id")).isEqualTo(cervezasId);
        assertThat(getProductLong(productId, "subcategory_id")).isEqualTo(getSubcategoryIdByName(cervezasId, "Latas"));
    }

    @Test
    void backfillLeavesUnmatchedLegacyUntouched() throws SQLException {
        long productId = insertProduct("Algo", "Bebida Fantasma");
        dbManager.initSchema();
        assertThat(getProductLong(productId, "category_id")).isNull();
        assertThat(getProductLong(productId, "subcategory_id")).isNull();
        assertThat(getLegacyCategory(productId)).isEqualTo("Bebida Fantasma");
    }

    @Test
    void backfillDoesNotOverwriteLegacyCategory() throws SQLException {
        long productId = insertProduct("Cerveza", "cervezas");
        dbManager.initSchema();
        assertThat(getProductLong(productId, "category_id")).isEqualTo(getCategoryIdByName("Cervezas"));
        assertThat(getLegacyCategory(productId)).isEqualTo("cervezas");
    }

    private long countRows(String table) throws SQLException {
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long getCategoryIdByName(String name) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "SELECT id FROM categories WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong("id");
            }
        }
    }

    private long getSubcategoryIdByName(long categoryId, String name) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "SELECT id FROM subcategories WHERE category_id = ? AND name = ?")) {
            ps.setLong(1, categoryId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong("id");
            }
        }
    }

    private Set<String> getSubcategoryNames(long categoryId) throws SQLException {
        Set<String> names = new HashSet<>();
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "SELECT name FROM subcategories WHERE category_id = ?")) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
            }
        }
        return names;
    }

    private long insertProduct(String name, String category) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "INSERT INTO products (name, category, presentation, cost_price, sale_price) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setString(3, "Botella");
            ps.setDouble(4, 100.0);
            ps.setDouble(5, 200.0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private Long getProductLong(long productId, String column) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "SELECT " + column + " FROM products WHERE id = ?")) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                long value = rs.getLong(column);
                return rs.wasNull() ? null : value;
            }
        }
    }

    private String getLegacyCategory(long productId) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "SELECT category FROM products WHERE id = ?")) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString("category");
            }
        }
    }
}
