package com.softwaredebebidas.util;

import com.softwaredebebidas.repository.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrityCheckerTest {

    private DatabaseManager dbManager;
    private IntegrityChecker checker;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
        checker = new IntegrityChecker(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void freshDatabaseIsHealthy() {
        assertThat(checker.isHealthy()).isTrue();
    }

    @Test
    void fullCheckReturnsNoIssues() {
        List<String> issues = checker.runFullCheck();
        assertThat(issues).isNotEmpty();
        assertThat(issues.get(0)).contains("No issues");
    }

    @Test
    void fullCheckHandlesMissingTableGracefully() throws Exception {
        try (var stmt = dbManager.getConnection().createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS sale_items");
        }
        List<String> issues = checker.runFullCheck();
        assertThat(issues).isNotEmpty();
    }

    @Test
    // REGRESION B2/B5 (audit v3): IntegrityChecker no compara current_stock contra
    // lo recalculado desde stock_movements. Este test falla hoy porque ese chequeo
    // todavía no existe — describe la funcionalidad faltante, no un bug de un método existente.
    void detectsCurrentStockDesync() throws Exception {
        try (var stmt = dbManager.getConnection().createStatement()) {
            stmt.execute("INSERT INTO products (id, name, category, presentation, cost_price, sale_price, min_stock, active, current_stock) "
                    + "VALUES (1, 'Test Product', 'Cat', 'Unidad', 10, 20, 0, 1, 5)");
            stmt.execute("INSERT INTO stock_movements (product_id, movement_type, quantity, reference_type) "
                    + "VALUES (1, 'ENTRY', 5, 'PURCHASE')");
            // stored current_stock (5) matches computed (5) so far; now force a desync
            // the same way InventoryService.adjustStock's missing updateStock() call would leave it.
            stmt.execute("UPDATE products SET current_stock = current_stock + 999 WHERE id = 1");
        }

        List<String> issues = checker.runFullCheck();

        assertThat(issues).anyMatch(i -> i.toLowerCase().contains("stock"));
    }
}
