package com.cocolatan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CsvServiceTest {

    private Path tempDir;
    private CsvService csvService;

    @BeforeEach
    void setUp() {
        tempDir = Path.of(System.getProperty("java.io.tmpdir"), "cocolatan-csv-test-" + UUID.randomUUID());
        csvService = new CsvService(tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                      .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception e) { } });
            }
        }
    }

    @Test
    void exportCreatesCsvFile() throws Exception {
        List<String> headers = List.of("ID", "Nombre", "Precio");
        List<List<String>> rows = List.of(
            List.of("1", "Coca-Cola", "600"),
            List.of("2", "Pepsi", "550")
        );

        Path file = csvService.export("productos", headers, rows, r -> r);

        assertThat(file).exists();
        assertThat(file.toString()).endsWith(".csv");
        assertThat(file.toString()).contains("productos_");
    }

    @Test
    void exportCreatesValidCsvContent() throws Exception {
        List<String> headers = List.of("Nombre", "Precio");
        List<List<String>> rows = List.of(
            List.of("Coca-Cola", "600.00")
        );

        Path file = csvService.export("test", headers, rows, r -> r);

        String content = Files.readString(file);
        assertThat(content).contains("Nombre;Precio");
        assertThat(content).contains("Coca-Cola;600.00");
    }

    @Test
    void exportHandlesSpecialCharacters() throws Exception {
        List<String> headers = List.of("Descripción");
        List<List<String>> rows = List.of(
            List.of("Bebida con \"sabor\" cola; muy rica")
        );

        Path file = csvService.export("special", headers, rows, r -> r);

        String content = Files.readString(file);
        assertThat(content).contains("\"Bebida con \"\"sabor\"\" cola; muy rica\"");
    }

    @Test
    void exportContainsBom() throws Exception {
        List<String> headers = List.of("Nombre");
        List<List<String>> rows = List.of(List.of("test"));
        Path file = csvService.export("bom", headers, rows, r -> r);

        byte[] bytes = Files.readAllBytes(file);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    void exportHandlesEmptyRows() throws Exception {
        List<String> headers = List.of("Nombre", "Precio");
        List<List<String>> rows = List.of();
        Path file = csvService.export("empty", headers, rows, r -> r);

        String content = Files.readString(file);
        assertThat(content).contains("Nombre;Precio");
    }
}
