package com.softwaredebebidas.util;

import com.softwaredebebidas.repository.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IntegrityChecker {

    private static final Logger LOGGER = Logger.getLogger(IntegrityChecker.class.getName());

    private final DatabaseManager dbManager;

    public IntegrityChecker(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public List<String> runFullCheck() {
        List<String> issues = new ArrayList<>();

        // Decision (audit Fase 3): the SQL below is fully static — hardcoded literals
        // with no user input or dynamic values. There is nothing to parameterize, so no
        // PreparedStatement is required and there is no injection surface to close.
        try (Statement stmt = dbManager.getConnection().createStatement()) {
            try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                while (rs.next()) {
                    String result = rs.getString(1);
                    if (!"ok".equalsIgnoreCase(result.trim())) {
                        issues.add("Integrity check: " + result);
                    }
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT si.id FROM sale_items si LEFT JOIN sales s ON si.sale_id = s.id WHERE s.id IS NULL")) {
                while (rs.next()) {
                    issues.add("Orphaned sale_item: id=" + rs.getLong(1));
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT pi.id FROM purchase_items pi LEFT JOIN purchases p ON pi.purchase_id = p.id WHERE p.id IS NULL")) {
                while (rs.next()) {
                    issues.add("Orphaned purchase_item: id=" + rs.getLong(1));
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT sm.id FROM stock_movements sm LEFT JOIN products p ON sm.product_id = p.id WHERE p.id IS NULL")) {
                while (rs.next()) {
                    issues.add("Stock movement with missing product: id=" + rs.getLong(1));
                }
            }

            // Denormalized current_stock (migration v14) must always match the real
            // net of stock_movements — this is the check that would have caught the
            // InventoryService.adjustStock() desync bug (audit v3, B2) in production.
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT p.id, p.name, p.current_stock AS stored, "
                    + "COALESCE(SUM(CASE WHEN m.movement_type IN ('ENTRY','ADJUSTMENT') THEN m.quantity ELSE 0 END), 0) "
                    + "- COALESCE(SUM(CASE WHEN m.movement_type = 'EXIT' THEN m.quantity ELSE 0 END), 0) AS computed "
                    + "FROM products p LEFT JOIN stock_movements m ON m.product_id = p.id "
                    + "GROUP BY p.id, p.name, p.current_stock "
                    + "HAVING stored != computed")) {
                while (rs.next()) {
                    issues.add("Stock cache desync: producto '" + rs.getString("name") + "' (id=" + rs.getLong("id")
                            + ") current_stock=" + rs.getInt("stored") + " pero calculado=" + rs.getInt("computed"));
                }
            }

            if (issues.isEmpty()) {
                issues.add("No issues found — database integrity OK");
            }

        } catch (SQLException e) {
            issues.add("Error running integrity check: " + e.getMessage());
            LOGGER.warning("Integrity check failed: " + e.getMessage());
        }

        return issues;
    }

    public boolean isHealthy() {
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            while (rs.next()) {
                if (!"ok".equalsIgnoreCase(rs.getString(1).trim())) {
                    return false;
                }
            }
            return true;
        } catch (SQLException e) {
            // Safe to fail closed: a DB that cannot run the check is not healthy.
            // Logged so the failure is observable instead of swallowed.
            LOGGER.log(Level.FINE, "Integrity check could not run; reporting unhealthy", e);
            return false;
        }
    }
}
