package io.github.lawrencekcleung.pft.service;

import java.util.List;

/**
 * The outcome of an import: how many rows were stored and which rows were skipped.
 *
 * @param imported the number of rows committed
 * @param errors   the skipped rows, in source order
 */
public record ImportReport(int imported, List<ImportError> errors) {

    /**
     * Validates and freezes the report.
     *
     * @throws IllegalArgumentException if {@code imported} is negative
     */
    public ImportReport {
        if (imported < 0) {
            throw new IllegalArgumentException("imported must be non-negative: " + imported);
        }
        errors = List.copyOf(errors);
    }

    /**
     * Returns how many rows were skipped.
     *
     * @return the number of source rows that failed validation
     */
    public int skipped() {
        return errors.size();
    }

    /**
     * Returns whether any row was skipped.
     *
     * @return {@code true} when at least one row failed
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
