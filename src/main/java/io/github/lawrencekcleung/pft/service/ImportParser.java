package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import io.github.lawrencekcleung.pft.store.CsvCodec;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an import file.
 *
 * <p>Schema: {@code date,type,amount,description[,category]}. The header is optional and is
 * recognised when the first field of the first non-blank line is {@code date} (case-insensitive);
 * a first field that parses as a date means the line is data. Fields use the same RFC 4180
 * quoting as the store, so a description may contain a comma. Import rows are line-oriented:
 * a quoted field may not span lines.</p>
 *
 * <p>Every row is validated independently. A bad row becomes an {@link ImportError} carrying its
 * source line and is skipped, so valid rows are never lost to one typo. A leading {@code id}
 * column, if the source has one, is ignored; the store always assigns fresh ids.</p>
 */
public final class ImportParser {

    private static final int MIN_COLUMNS = 4;
    private static final int MAX_COLUMNS = 5;

    /**
     * Parses the whole file.
     *
     * @param content the file content; {@code null} or empty means no rows
     * @return the valid drafts and the skipped rows
     */
    public ParsedImport parse(String content) {
        if (content == null || content.isEmpty()) {
            return new ParsedImport(List.of(), List.of());
        }
        String normalized = content.startsWith("\uFEFF") ? content.substring(1) : content;
        String[] lines = normalized.split("\n", -1);

        List<Transaction> drafts = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        boolean firstContentLine = true;
        for (int i = 0; i < lines.length; i++) {
            String line = stripCarriageReturn(lines[i]);
            if (line.isBlank()) {
                continue;
            }
            if (firstContentLine) {
                firstContentLine = false;
                if (isHeader(line)) {
                    continue;
                }
            }
            try {
                drafts.add(parseRow(line));
            } catch (IllegalArgumentException e) {
                errors.add(new ImportError(i + 1, e.getMessage()));
            }
        }
        return new ParsedImport(drafts, errors);
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /**
     * Recognises the optional header. A first field of {@code date} (or {@code id}, when the
     * source carries an id column) counts; anything else is treated as a data row so a typo in the
     * first date is reported rather than silently swallowed as a header.
     */
    private static boolean isHeader(String line) {
        try {
            List<String> fields = CsvCodec.splitFields(line);
            if (fields.isEmpty()) {
                return false;
            }
            String first = fields.get(0).trim();
            return first.equalsIgnoreCase("date") || first.equalsIgnoreCase("id");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Transaction parseRow(String line) {
        List<String> fields = CsvCodec.splitFields(line);
        if (fields.size() > MIN_COLUMNS && !isDateField(fields.get(0))
                && isDateField(fields.get(1))) {
            // A leading id column is ignored; the store always assigns fresh ids.
            fields = fields.subList(1, fields.size());
        }
        if (fields.size() < MIN_COLUMNS || fields.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException(
                    "expected 4 or 5 columns, got " + fields.size());
        }
        LocalDate date = parseDate(fields.get(0));
        TransactionType type = TransactionType.fromWire(fields.get(1).trim());
        Money amount = Money.parse(fields.get(2));
        String description = fields.get(3);
        String category = fields.size() == MAX_COLUMNS ? fields.get(4) : null;
        return new Transaction(0, date, type, amount, category, description);
    }

    private static boolean isDateField(String raw) {
        try {
            LocalDate.parse(raw.trim());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "invalid date '" + raw + "': expected yyyy-MM-dd");
        }
    }
}
