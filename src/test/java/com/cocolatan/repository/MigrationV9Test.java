package com.cocolatan.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationV9Test {

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
    void migrationV9AddsPaymentMethodColumn() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet columns = meta.getColumns(null, null, "purchases", "payment_method")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("TYPE_NAME")).isEqualTo("TEXT");
        }
    }

    @Test
    void migrationV9IsIdempotent() throws SQLException {
        dbManager.initSchema();
        dbManager.initSchema();

        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet columns = meta.getColumns(null, null, "purchases", "payment_method")) {
            assertThat(columns.next()).isTrue();
        }
    }

    @Test
    void paymentMethodColumnIsNullable() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet columns = meta.getColumns(null, null, "purchases", "payment_method")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
        }
    }
}
