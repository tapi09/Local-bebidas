package com.softwaredebebidas.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationV8Test {

    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void migrationV8AddsInvoicePhotoPathColumn() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet columns = meta.getColumns(null, null, "purchases", "invoice_photo_path")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("TYPE_NAME")).isEqualTo("TEXT");
        }
    }

    @Test
    void migrationV8CreatesRegisterSessionsTable() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet tables = meta.getTables(null, null, "register_sessions", null)) {
            assertThat(tables.next()).isTrue();
        }
    }

    @Test
    void migrationV8CreatesRegisterSessionsIndexes() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet indexes = meta.getIndexInfo(null, null, "register_sessions", false, true)) {
            boolean hasStatusIndex = false;
            boolean hasOpenDateIndex = false;
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if ("idx_register_sessions_status".equals(indexName)) hasStatusIndex = true;
                if ("idx_register_sessions_open_date".equals(indexName)) hasOpenDateIndex = true;
            }
            assertThat(hasStatusIndex).isTrue();
            assertThat(hasOpenDateIndex).isTrue();
        }
    }

    @Test
    void registerSessionsTableHasExpectedColumns() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet columns = meta.getColumns(null, null, "register_sessions", null)) {
            java.util.Set<String> columnNames = new java.util.HashSet<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }
            assertThat(columnNames).contains(
                    "id", "register_name", "open_date", "close_date",
                    "status", "initial_cash", "expected_cash", "actual_cash",
                    "created_at", "closed_at"
            );
        }
    }

    @Test
    void registerSessionsTableHasStatusCheckConstraint() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet columns = meta.getColumns(null, null, "register_sessions", "status")) {
            assertThat(columns.next()).isTrue();
        }
    }

    @Test
    void migrationV8IsIdempotent() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet tables = meta.getTables(null, null, "register_sessions", null)) {
            assertThat(tables.next()).isTrue();
        }

        try (ResultSet columns = meta.getColumns(null, null, "purchases", "invoice_photo_path")) {
            assertThat(columns.next()).isTrue();
        }
    }
}