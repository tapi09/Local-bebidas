package com.softwaredebebidas.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationV10Test {

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
    void migrationV10AddsBusinessNameConfig() throws SQLException {
        ConfigRepository configRepo = new ConfigRepository(dbManager);
        Optional<String> value = configRepo.get("business_name");
        assertThat(value).isPresent();
        assertThat(value.get()).isEqualTo("Ruta 40 bebidas");
    }

    @Test
    void migrationV10IsIdempotent() throws SQLException {
        dbManager.initSchema();
        dbManager.initSchema();

        ConfigRepository configRepo = new ConfigRepository(dbManager);
        Optional<String> value = configRepo.get("business_name");
        assertThat(value).isPresent();
        assertThat(value.get()).isEqualTo("Ruta 40 bebidas");
    }

    @Test
    void businessNameCanBeUpdated() throws SQLException {
        ConfigRepository configRepo = new ConfigRepository(dbManager);
        configRepo.set("business_name", "Mi Negocio");
        Optional<String> value = configRepo.get("business_name");
        assertThat(value).isPresent();
        assertThat(value.get()).isEqualTo("Mi Negocio");
    }
}
