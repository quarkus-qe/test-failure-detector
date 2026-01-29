package io.quarkus.qe.test.failure.detector.cli;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

final class CommandUtils {

    private CommandUtils() {
    }

    /**
     * Parses the FROM date as a date string in various formats to an Instant.
     * Supports:
     * - dd.MM.yyyy (e.g., 10.1.2026)
     * - yyyy-MM-dd (e.g., 2026-01-10)
     * - ISO-8601 instant (e.g., 2026-01-10T00:00:00Z)
     * Returns null if from is null, allowing the caller to determine the reference time.
     */
    static Instant parseDate(String from) {
        if (from == null) {
            return null;
        }

        // Try ISO-8601 instant first
        try {
            return Instant.parse(from);
        } catch (DateTimeParseException e) {
            // Not an instant, try date formats
        }

        // Try dd.MM.yyyy format
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d.M.yyyy");
            LocalDate date = LocalDate.parse(from, formatter);
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            // Not dd.MM.yyyy format
        }

        // Try yyyy-MM-dd format
        try {
            LocalDate date = LocalDate.parse(from);
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid date format: '" + from + "'. " +
                            "Expected formats: dd.MM.yyyy (e.g., 10.1.2026), yyyy-MM-dd (e.g., 2026-01-10), " +
                            "or ISO-8601 instant (e.g., 2026-01-10T00:00:00Z)");
        }
    }
}
