package com.cocolatan.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for date formatting and parsing in DD/MM/YYYY format.
 * Uses java.time.LocalDate for thread-safe date handling.
 */
public final class DateUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private DateUtils() {
        // Utility class — no instantiation
    }

    /**
     * Formats a LocalDate to ISO-8601 (yyyy-MM-dd) — the storage/query format
     * for sale_date and purchase_date. Use this (never {@link #format}) for
     * anything that gets persisted to or compared against those columns.
     */
    public static String toIso(LocalDate date) {
        if (date == null) {
            throw new NullPointerException("Date must not be null");
        }
        return date.format(ISO_FORMATTER);
    }

    /**
     * Converts an ISO-8601 (yyyy-MM-dd) stored date to DD/MM/YYYY for display.
     */
    public static String toDisplay(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return "";
        }
        return LocalDate.parse(isoDate, ISO_FORMATTER).format(FORMATTER);
    }

    /**
     * Today's date in ISO-8601 (yyyy-MM-dd) — for sale_date/purchase_date queries.
     */
    public static String todayIso() {
        return LocalDate.now().format(ISO_FORMATTER);
    }

    /**
     * Formats a LocalDate to DD/MM/YYYY string.
     *
     * @param date the date to format
     * @return formatted string like "22/07/2026"
     */
    public static String format(LocalDate date) {
        if (date == null) {
            throw new NullPointerException("Date must not be null");
        }
        return date.format(FORMATTER);
    }

    /**
     * Parses a DD/MM/YYYY string to LocalDate.
     *
     * @param text the date string to parse
     * @return LocalDate instance
     * @throws IllegalArgumentException if the string is not in DD/MM/YYYY format
     */
    public static LocalDate parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Date string must not be empty");
        }
        try {
            return LocalDate.parse(text, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Expected DD/MM/YYYY, got: " + text, e);
        }
    }
}
