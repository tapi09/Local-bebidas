package com.cocolatan;

import com.cocolatan.repository.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeTest {

    @Test
    void databaseManagerInMemoryInitializes() {
        DatabaseManager db = DatabaseManager.createInMemory();
        assertThat(db).isNotNull();
        assertThat(db.getConnection()).isNotNull();
        db.close();
    }

    @Test
    void schemaVersionTableExists() throws Exception {
        DatabaseManager db = DatabaseManager.createInMemory();
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isGreaterThan(0);
        }
        db.close();
    }
}
