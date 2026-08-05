package com.cocolatan.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationV12Test {

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
    void migrationV12DoesNotSeedMasterResetHash() throws SQLException {
        ConfigRepository configRepo = new ConfigRepository(dbManager);

        assertThat(configRepo.get("master_reset_hash")).isEmpty();
    }

    @Test
    void migrationV12IsIdempotent() throws SQLException {
        dbManager.initSchema();
        dbManager.initSchema();

        ConfigRepository configRepo = new ConfigRepository(dbManager);
        assertThat(configRepo.get("master_reset_hash")).isEmpty();
    }
}
