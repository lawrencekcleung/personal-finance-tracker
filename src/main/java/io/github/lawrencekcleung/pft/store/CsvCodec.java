package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Translates between {@link Transaction} values and the persisted CSV format.
 *
 * <p>Format contract:</p>
 * <pre>
 * id,date,type,amount,category,description
 * 1,2026-11-01,income,5000.00,income,Salary Oct
 * 2,2026-11-01,expense,42.50,groceries,"Countdown, Auckland"
 * </pre>
 *
 * <p>Fields are RFC 4180 quoted when they contain a comma, a double quote, or a newline; embedded
 * quotes are doubled. Line endings may be LF or CRLF, and a UTF-8 BOM at the start is ignored.
 * Amounts are canonical 2dp text, and a description may contain newlines (the reader tracks record
 * boundaries rather than physical lines).</p>
 *
 * <p>Malformed content raises {@link StorageException} whose message is {@code N: detail}, where
 * {@code N} is the 1-based line the record starts on. The repository prefixes the file path so
 * users see the PRD's {@code data/transactions.csv:7: ...}.</p>
 */
public final class CsvCodec {

    /** The header line, always written first and required when reading. */
    public static final String HEADER = "id,date,type,amount,category,description";

    /**
     * Returns the header line.
     *
     * @return the header, without a trailing newline
     */
    public String header() {
        return HEADER;
    }

    /**
     * Renders a transaction as one CSV record, without a trailing newline.
     *
     * @param tx the transaction to encode
     * @return the record
     */
    public String encode(Transaction tx) {
        return String.join(",",
                Integer.toString(tx.id()),
                tx.date().toString(),
                tx.type().wireName(),
                tx.amount().format(),
                quote(tx.category()),
                quote(tx.description()));
    }

    /**
     * Parses a whole file.
     *
     * @param content the file content; {@code null} or empty means an empty store
     * @return the transactions in file order, immutable
     * @throws StorageException if the header or any record is malformed
     */
    public List<Transaction> parse(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        String normalized = stripBom(content).replace("\r\n", "\n");
        List<Record> records = splitRecords(normalized);
        if (records.isEmpty()) {
            return List.of();
        }
        Record headerRecord = records.get(0);
        if (!headerRecord.text().trim().equals(HEADER)) {
            throw malformed(headerRecord.lineNumber(), "expected header '" + HEADER + "'");
        }
        List<Transaction> transactions = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();
        for (int i = 1; i < records.size(); i++) {
            Record record = records.get(i);
            if (record.text().isBlank()) {
                continue;
            }
            Transaction tx = parseRecord(record);
            if (!seenIds.add(tx.id())) {
                throw malformed(record.lineNumber(), "duplicate id " + tx.id());
            }
            transactions.add(tx);
        }
        return List.copyOf(transactions);
    }

    private Transaction parseRecord(Record record) {
        List<String> fields;
        try {
            fields = splitFields(record.text());
        } catch (IllegalArgumentException e) {
            throw malformed(record.lineNumber(), e.getMessage());
        }
        if (fields.size() != 6) {
            throw malformed(record.lineNumber(), "expected 6 columns, got " + fields.size());
        }
        int id = parseId(fields.get(0), record.lineNumber());
        LocalDate date = parseDate(fields.get(1), record.lineNumber());
        TransactionType type = parseType(fields.get(2), record.lineNumber());
        Money amount = parseAmount(fields.get(3), record.lineNumber());
        try {
            return new Transaction(id, date, type, amount, fields.get(4), fields.get(5));
        } catch (IllegalArgumentException e) {
            throw malformed(record.lineNumber(), e.getMessage());
        }
    }

    private static int parseId(String raw, int lineNumber) {
        int id;
        try {
            id = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw malformed(lineNumber, "invalid id '" + raw + "'");
        }
        if (id <= 0) {
            throw malformed(lineNumber, "invalid id '" + raw + "': must be positive");
        }
        return id;
    }

    private static LocalDate parseDate(String raw, int lineNumber) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw malformed(lineNumber, "invalid date '" + raw + "': expected yyyy-MM-dd");
        }
    }

    private static TransactionType parseType(String raw, int lineNumber) {
        try {
            return TransactionType.fromWire(raw.trim());
        } catch (IllegalArgumentException e) {
            throw malformed(lineNumber, e.getMessage());
        }
    }

    private static Money parseAmount(String raw, int lineNumber) {
        try {
            return Money.parse(raw);
        } catch (IllegalArgumentException e) {
            throw malformed(lineNumber, e.getMessage());
        }
    }

    private static StorageException malformed(int lineNumber, String detail) {
        return new StorageException(lineNumber + ": " + detail);
    }

    /**
     * Splits one already-delimited record into fields, honouring RFC 4180 quoting. A quoted field
     * may contain commas, doubled quotes and newlines.
     *
     * <p>The text must not include the record separator. A caller that knows the source location
     * attaches it to the failure; the store does this to produce {@code path:7: ...}.</p>
     *
     * @param text the record text
     * @return the field values in order
     * @throws IllegalArgumentException if a quoted field is not terminated
     */
    public static List<String> splitFields(String text) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("unterminated quoted field");
        }
        fields.add(current.toString());
        return fields;
    }

    /**
     * Splits the file into records, tracking the line each record starts on. Newlines inside
     * quoted fields belong to the record, not to a new one.
     */
    private static List<Record> splitRecords(String content) {
        List<Record> records = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int line = 1;
        int startLine = 1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') {
                current.append(c);
                if (inQuotes && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == '\n' && !inQuotes) {
                records.add(new Record(startLine, current.toString()));
                current.setLength(0);
                line++;
                startLine = line;
            } else {
                current.append(c);
                if (c == '\n') {
                    line++;
                }
            }
        }
        if (!current.isEmpty()) {
            records.add(new Record(startLine, current.toString()));
        }
        return records;
    }

    private static String quote(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String stripBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }

    /** A record and the 1-based line it starts on. */
    private record Record(int lineNumber, String text) {
    }
}
