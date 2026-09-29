package com.softwaredebebidas.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic CSV export service with UTF-8 BOM support.
 * Uses semicolon (;) as delimiter for Excel compatibility.
 */
public class CsvService {

    private static final String BOM = "\uFEFF";
    private static final String SEPARATOR = ";";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Path exportDir;

    public CsvService(Path exportDir) {
        this.exportDir = exportDir;
    }

    /**
     * Exports a list of items to a CSV file.
     *
     * @param prefix  filename prefix (e.g., "ventas", "productos")
     * @param headers column headers
     * @param rows    list of items to export
     * @param mapper  function that converts an item to a CSV row
     * @return the path to the created CSV file
     */
    public <T> Path export(String prefix, List<String> headers, List<T> rows, Function<T, List<String>> mapper) throws IOException {
        Files.createDirectories(exportDir);

        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        Path file = exportDir.resolve(prefix + "_" + timestamp + ".csv");

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(BOM);

            writer.write(headers.stream().collect(Collectors.joining(SEPARATOR)));
            writer.newLine();

            for (T row : rows) {
                List<String> values = mapper.apply(row);
                String line = values.stream()
                    .map(v -> v != null ? escapeField(v) : "")
                    .collect(Collectors.joining(SEPARATOR));
                writer.write(line);
                writer.newLine();
            }
        }

        return file;
    }

    public static String nowFormatted() {
        return LocalDateTime.now().format(DATE_FMT);
    }

    private String escapeField(String value) {
        if (value.contains(SEPARATOR) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
