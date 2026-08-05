package com.cocolatan.util;

import com.cocolatan.repository.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrityCheckerTest {

    private DatabaseManager dbManager;
    private IntegrityChecker checker;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
        checker = new IntegrityChecker(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void freshDatabaseIsHealthy() {
        assertThat(checker.isHealthy()).isTrue();
    }

    @Test
    void fullCheckReturnsNoIssues() {
        List<String> issues = checker.runFullCheck();
        assertThat(issues).isNotEmpty();
        assertThat(issues.get(0)).contains("No issues");
    }

    @Test
    void fullCheckHandlesMissingTableGracefully() throws Exception {
        try (var stmt = dbManager.getConnection().createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS sale_items");
        }
        List<String> issues = checker.runFullCheck();
        assertThat(issues).isNotEmpty();
    }
}
