package com.softwaredebebidas.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REGRESION B5 (audit v3): reproduces, with real SQLite behavior (not assumed),
 * what happens when the migration v14 current_stock backfill UPDATE hits a product
 * whose net stock_movements are negative — which the CHECK (current_stock >= 0)
 * on the column rejects.
 */
class MigrationV14CheckConstraintTest {

    private DatabaseManager dbManager;

    // Exact backfill statement from DatabaseManager.initSchema() migration v14.
    private static final String BACKFILL_SQL = "UPDATE products SET current_stock = ("
            + "SELECT COALESCE(SUM(CASE WHEN m.movement_type IN ('ENTRY','ADJUSTMENT') THEN m.quantity ELSE 0 END), 0) "
            + "- COALESCE(SUM(CASE WHEN m.movement_type = 'EXIT' THEN m.quantity ELSE 0 END), 0) "
            + "FROM stock_movements m WHERE m.product_id = products.id)";

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void backfillAbortsEntirelyAndRollsBackAlreadyUpdatedRowsWhenOneProductGoesNegative() throws SQLException {
        try (Statement stmt = dbManager.getConnection().createStatement()) {
            // Product 1: healthy, net +10 (would compute fine).
            stmt.execute("INSERT INTO products (id, name, category, presentation, cost_price, sale_price, min_stock, active, current_stock) "
                    + "VALUES (1, 'Producto Sano', 'Cat', 'Unidad', 10, 20, 0, 1, 0)");
            stmt.execute("INSERT INTO stock_movements (product_id, movement_type, quantity, reference_type) "
                    + "VALUES (1, 'ENTRY', 10, 'PURCHASE')");

            // Product 2: reproduces the real-world risk — more EXIT than ENTRY, e.g. from
            // adjustStock()'s EXIT branch never calling updateStock (bug B2), net = -3.
            stmt.execute("INSERT INTO products (id, name, category, presentation, cost_price, sale_price, min_stock, active, current_stock) "
                    + "VALUES (2, 'Producto Con Neto Negativo', 'Cat', 'Unidad', 10, 20, 0, 1, 0)");
            stmt.execute("INSERT INTO stock_movements (product_id, movement_type, quantity, reference_type) "
                    + "VALUES (2, 'EXIT', 3, 'SALE')");
        }

        // Sanity check: product ids ascend 1 then 2, so if SQLite processes rows in
        // rowid order, product 1 (healthy) is written before product 2 (violating) hits the CHECK.
        try (Statement stmt = dbManager.getConnection().createStatement()) {
            assertThatThrownBy(() -> stmt.executeUpdate(BACKFILL_SQL))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("CHECK");
        }

        // The critical evidence: does product 1 (which individually would have computed
        // fine) still show the backfilled value, or did SQLite's ABORT conflict resolution
        // roll back the ENTIRE statement, including rows already written before the failure?
        try (Statement stmt = dbManager.getConnection().createStatement();
             var rs = stmt.executeQuery("SELECT id, current_stock FROM products ORDER BY id")) {
            int product1Stock = -999;
            int product2Stock = -999;
            while (rs.next()) {
                if (rs.getInt("id") == 1) product1Stock = rs.getInt("current_stock");
                if (rs.getInt("id") == 2) product2Stock = rs.getInt("current_stock");
            }
            // If this assertion PASSES, it proves the whole backfill is atomic-void on
            // failure: product 1 never got its correct +10, confirming that runMigration()
            // swallowing this SQLException leaves EVERY product at current_stock=0 (the
            // column DEFAULT), not just the one product that caused the violation.
            assertThat(product1Stock).as("producto sano, backfill esperado=10 pero abortado por el otro producto").isEqualTo(0);
            assertThat(product2Stock).as("producto con neto negativo, nunca llega a violar visiblemente porque el UPDATE completo se descarta").isEqualTo(0);
        }
    }
}
