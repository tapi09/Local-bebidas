package com.cocolatan.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists dismissed alerts so they do not reappear after a restart.
 * A dismissal is keyed by (alert_type, product_id, lot_number).
 */
public class AlertDismissalRepository {

    private final DatabaseManager dbManager;

    public AlertDismissalRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Records a dismissal. Idempotent: the UNIQUE key ignores repeats.
     */
    public void dismiss(String alertType, Long productId, String lotNumber) throws SQLException {
        String sql = "INSERT OR IGNORE INTO alert_dismissals (alert_type, product_id, lot_number) VALUES (?, ?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alertType);
            ps.setLong(2, productId);
            if (lotNumber != null) {
                ps.setString(3, lotNumber);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Returns all persisted dismissals as lightweight records (id + composite key parts).
     */
    public List<Dismissal> findAll() throws SQLException {
        String sql = "SELECT id, alert_type, product_id, lot_number FROM alert_dismissals";
        Connection conn = dbManager.getConnection();
        List<Dismissal> dismissals = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                dismissals.add(new Dismissal(
                        rs.getLong("id"),
                        rs.getString("alert_type"),
                        rs.getLong("product_id"),
                        rs.getString("lot_number")
                ));
            }
        }
        return dismissals;
    }

    /**
     * Deletes every dismissal whose id is NOT in keepIds. Used as opportunistic
     * cleanup: dismissals for alerts that no longer apply are removed so the
     * alert can fire again if the condition returns (e.g. stock refilled and
     * later dropped again).
     */
    public void deleteAllExcept(List<Long> keepIds) throws SQLException {
        if (keepIds == null || keepIds.isEmpty()) {
            String sql = "DELETE FROM alert_dismissals";
            Connection conn = dbManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.executeUpdate();
            }
            return;
        }
        String placeholders = keepIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "DELETE FROM alert_dismissals WHERE id NOT IN (" + placeholders + ")";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < keepIds.size(); i++) {
                ps.setLong(i + 1, keepIds.get(i));
            }
            ps.executeUpdate();
        }
    }

    /**
     * Lightweight dismissal record with the composite key parts exposed.
     */
    public static class Dismissal {
        private final long id;
        private final String alertType;
        private final long productId;
        private final String lotNumber;

        public Dismissal(long id, String alertType, long productId, String lotNumber) {
            this.id = id;
            this.alertType = alertType;
            this.productId = productId;
            this.lotNumber = lotNumber;
        }

        public long getId() { return id; }
        public String getAlertType() { return alertType; }
        public long getProductId() { return productId; }
        public String getLotNumber() { return lotNumber; }

        /** Composite key: type|productId|lot (lot normalized to empty string when null). */
        public String key() {
            return alertType + "|" + productId + "|" + (lotNumber == null ? "" : lotNumber);
        }
    }
}