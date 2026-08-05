package com.cocolatan.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("cocolatan-logtest-");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var files = Files.walk(tempDir)) {
            files.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                            // ignore
                        }
                    });
        }

        LoggingConfig.reset();

        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) {
            if (h instanceof FileHandler) {
                root.removeHandler(h);
                h.close();
            }
        }
    }

    @Test
    @DisplayName("init crea archivo .log en el directorio especificado")
    void initCreatesLogFile() {
        LoggingConfig.init(tempDir.toString());

        Logger logger = Logger.getLogger(LoggingConfigTest.class.getName());
        logger.info("Test log message");

        Path[] logs = new Path[0];
        try (var stream = Files.list(tempDir)) {
            logs = stream.filter(p -> p.toString().endsWith(".log")).toArray(Path[]::new);
        } catch (Exception e) {
            // ignore
        }

        assertThat(logs).isNotEmpty();
    }

    @Test
    @DisplayName("init es idempotente — no agrega múltiples FileHandler")
    void initIsIdempotent() {
        LoggingConfig.init(tempDir.toString());
        LoggingConfig.init(tempDir.toString());

        Logger root = Logger.getLogger("");
        long fileHandlerCount = 0;
        for (Handler h : root.getHandlers()) {
            if (h instanceof FileHandler) fileHandlerCount++;
        }

        assertThat(fileHandlerCount).isEqualTo(1);
    }

    @Test
    @DisplayName("init no lanza cuando el directorio de logs no se puede crear (fallback System.err)")
    void initDoesNotThrowWhenLogDirCannotBeCreated() {
        // Pointing into a non-existent child of a plain file forces the IOException
        // path in init(); the method must degrade gracefully instead of propagating.
        Path fileAsDir = tempDir.resolve("not-a-directory.txt");
        try {
            Files.createFile(fileAsDir);
        } catch (Exception e) {
            // ignore
        }

        Path badLogDir = fileAsDir.resolve("logs");

        org.assertj.core.api.Assertions.assertThatCode(() -> LoggingConfig.init(badLogDir.toString()))
                .doesNotThrowAnyException();
    }
}
