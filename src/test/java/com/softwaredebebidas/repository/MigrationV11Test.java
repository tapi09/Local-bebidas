package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationV11Test {

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
    void migrationV11AddsMustChangePasswordColumn() throws SQLException {
        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet columns = meta.getColumns(null, null, "users", "must_change_password")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("TYPE_NAME")).isEqualTo("INTEGER");
        }
    }

    @Test
    void migrationV11IsIdempotent() throws SQLException {
        dbManager.initSchema();
        dbManager.initSchema();

        Connection conn = dbManager.getConnection();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet columns = meta.getColumns(null, null, "users", "must_change_password")) {
            assertThat(columns.next()).isTrue();
        }
    }

    @Test
    void mustChangePasswordColumnDefaultsToZero() throws SQLException {
        UserRepository repo = new UserRepository(dbManager);
        User user = new User();
        user.setUsername("nuevo");
        user.setPasswordHash("hash");
        user.setRole("CAJERO");
        user.setDisplayName("Nuevo");
        repo.save(user);

        try (PreparedStatement ps = dbManager.getConnection()
                .prepareStatement("SELECT must_change_password FROM users WHERE username = ?")) {
            ps.setString(1, "nuevo");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("must_change_password")).isEqualTo(0);
            }
        }
    }
}
