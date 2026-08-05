package com.cocolatan.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigRepositoryTest {

    private DatabaseManager dbManager;
    private ConfigRepository repository;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
        repository = new ConfigRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void getReturnsEmptyForUnknownKey() throws SQLException {
        assertThat(repository.get("dark_mode")).isEmpty();
    }

    @Test
    void getReturnsValueAfterSet() throws SQLException {
        repository.set("dark_mode", "true");

        assertThat(repository.get("dark_mode")).contains("true");
    }

    @Test
    void setOverwritesExistingValue() throws SQLException {
        repository.set("dark_mode", "true");
        repository.set("dark_mode", "false");

        assertThat(repository.get("dark_mode")).contains("false");
    }

    @Test
    void getReadsPersistedValueThroughNewInstance() throws SQLException {
        repository.set("dark_mode", "true");

        ConfigRepository fresh = new ConfigRepository(dbManager);

        assertThat(fresh.get("dark_mode")).contains("true");
        assertThat(repository.get("theme")).isEmpty();
    }

    @Test
    void masterResetHashRoundTrips() throws SQLException {
        String hash = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2a3b4c5d6a7b8c9d0e1f2";
        repository.set("master_reset_hash", hash);

        assertThat(repository.get("master_reset_hash")).contains(hash);
    }
}
