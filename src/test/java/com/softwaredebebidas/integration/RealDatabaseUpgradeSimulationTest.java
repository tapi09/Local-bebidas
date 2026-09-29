package com.softwaredebebidas.integration;

import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.util.IntegrityChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * One-off, manually-triggered simulation of "install this update over the
 * user's real, already-in-use database". Runs ONLY against a copy supplied
 * via -Dsoftwaredebebidas.real.db.copy=<path>; skipped otherwise so it never affects
 * the normal suite or CI. The copy is opened read-write (that's the point —
 * simulating the real upgrade), the original file is never touched by this
 * test.
 */
class RealDatabaseUpgradeSimulationTest {

    @Test
    void upgradingRealDatabaseCopyLosesNoDataAndFixesStockConsistency(@TempDir Path tempDir) throws Exception {
        String sourcePath = System.getProperty("softwaredebebidas.real.db.copy");
        assumeTrue(sourcePath != null && !sourcePath.isBlank(),
                "Skipped: pass -Dsoftwaredebebidas.real.db.copy=<path to a COPY of softwaredebebidas.db> to run this simulation");

        Path workingCopy = tempDir.resolve("upgrade-sim.db");
        Files.copy(Path.of(sourcePath), workingCopy);

        Map<String, Object> before = snapshot(workingCopy);

        // This is the exact call SoftwareDeBebidasApp.start() makes on every launch —
        // it runs initSchema(), which runs every migration up to the latest.
        DatabaseManager upgraded = DatabaseManager.createFromFile(workingCopy.toString());

        Map<String, Object> after = snapshot(workingCopy);

        System.out.println("=== ANTES DE LA ACTUALIZACION ===");
        before.forEach((k, v) -> System.out.println(k + " = " + v));
        System.out.println("=== DESPUES DE LA ACTUALIZACION ===");
        after.forEach((k, v) -> System.out.println(k + " = " + v));

        // Zero rows lost, zero cents moved, in every business table.
        assertThat(after.get("count_products")).isEqualTo(before.get("count_products"));
        assertThat(after.get("count_sales")).isEqualTo(before.get("count_sales"));
        assertThat(after.get("count_sale_items")).isEqualTo(before.get("count_sale_items"));
        assertThat(after.get("count_purchases")).isEqualTo(before.get("count_purchases"));
        assertThat(after.get("count_purchase_items")).isEqualTo(before.get("count_purchase_items"));
        assertThat(after.get("count_stock_movements")).isEqualTo(before.get("count_stock_movements"));
        assertThat(after.get("count_suppliers")).isEqualTo(before.get("count_suppliers"));
        assertThat(after.get("sum_sales_total")).isEqualTo(before.get("sum_sales_total"));
        assertThat(after.get("sum_purchases_total")).isEqualTo(before.get("sum_purchases_total"));

        // Every sale_date / purchase_date is now ISO (yyyy-MM-dd).
        assertThat((Long) after.get("non_iso_sale_dates")).isZero();
        assertThat((Long) after.get("non_iso_purchase_dates")).isZero();

        // The whole point of this release: current_stock now matches reality
        // for every product, and IntegrityChecker (extended in this release)
        // confirms it against the real data, not a synthetic fixture.
        List<String> issues = new IntegrityChecker(upgraded).runFullCheck();
        System.out.println("=== INTEGRITY CHECKER (post-actualizacion) ===");
        issues.forEach(System.out::println);
        boolean anyStockDesync = issues.stream().anyMatch(i -> i.contains("Stock cache desync"));
        assertThat(anyStockDesync)
                .as("current_stock debe coincidir con stock_movements para TODOS los productos reales")
                .isFalse();

        upgraded.close();
    }

    private Map<String, Object> snapshot(Path dbPath) throws SQLException {
        Map<String, Object> data = new LinkedHashMap<>();
        try (Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            data.put("count_products", scalarLong(conn, "SELECT COUNT(*) FROM products"));
            data.put("count_sales", scalarLong(conn, "SELECT COUNT(*) FROM sales"));
            data.put("count_sale_items", scalarLong(conn, "SELECT COUNT(*) FROM sale_items"));
            data.put("count_purchases", scalarLong(conn, "SELECT COUNT(*) FROM purchases"));
            data.put("count_purchase_items", scalarLong(conn, "SELECT COUNT(*) FROM purchase_items"));
            data.put("count_stock_movements", scalarLong(conn, "SELECT COUNT(*) FROM stock_movements"));
            data.put("count_suppliers", scalarLong(conn, "SELECT COUNT(*) FROM suppliers"));
            data.put("sum_sales_total", scalarDouble(conn, "SELECT COALESCE(SUM(total_amount),0) FROM sales"));
            data.put("sum_purchases_total", scalarDouble(conn, "SELECT COALESCE(SUM(total_amount),0) FROM purchases"));
            data.put("non_iso_sale_dates", scalarLong(conn,
                    "SELECT COUNT(*) FROM sales WHERE sale_date NOT LIKE '____-__-__'"));
            data.put("non_iso_purchase_dates", scalarLong(conn,
                    "SELECT COUNT(*) FROM purchases WHERE purchase_date NOT LIKE '____-__-__'"));
        }
        return data;
    }

    private long scalarLong(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private double scalarDouble(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return Math.round(rs.getDouble(1) * 100.0) / 100.0;
        }
    }
}
