package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.model.Category;
import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvCodecTest {

    private final CsvCodec codec = new CsvCodec();

    private static Transaction expense(String description) {
        return new Transaction(1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE,
                Money.ofCents(4250), "groceries", description);
    }

    private static StorageException failure(String content) {
        return assertThrows(StorageException.class, () -> new CsvCodec().parse(content));
    }

    // ---------------------------------------------------------------- encoding

    @Test
    void headerIsExact() {
        assertEquals("id,date,type,amount,category,description", codec.header());
    }

    @Test
    void encodesTransactionInColumnOrder() {
        Transaction tx = new Transaction(1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE,
                Money.ofCents(4250), "groceries", "Countdown");
        assertEquals("1,2026-11-01,expense,42.50,groceries,Countdown", codec.encode(tx));
    }

    @Test
    void quotesDescriptionsContainingCommas() {
        String line = codec.encode(expense("Countdown, Auckland"));
        assertEquals("1,2026-11-01,expense,42.50,groceries,\"Countdown, Auckland\"", line);
    }

    @Test
    void quotesEmbeddedDoubleQuotes() {
        String line = codec.encode(expense("He said \"hi\""));
        assertEquals("1,2026-11-01,expense,42.50,groceries,\"He said \"\"hi\"\"\"", line);
    }

    @Test
    void quotesCategoriesContainingCommas() {
        Transaction tx = new Transaction(2, LocalDate.of(2026, 11, 2), TransactionType.EXPENSE,
                Money.ofCents(1000), "eating, out", "Lunch");
        assertTrue(codec.encode(tx).contains("\"eating, out\""));
    }

    // ---------------------------------------------------------------- decoding

    @Test
    void emptyOrNullContentIsAnEmptyStore() {
        assertTrue(codec.parse("").isEmpty());
        assertTrue(codec.parse(null).isEmpty());
    }

    @Test
    void parsesHeaderAndRowsInFileOrder() {
        String content = CsvCodec.HEADER + "\n"
                + "1,2026-11-01,income,5000.00,income,Salary Oct\n"
                + "2,2026-11-01,expense,42.50,groceries,Countdown\n";

        List<Transaction> transactions = codec.parse(content);

        assertEquals(2, transactions.size());
        assertEquals(1, transactions.get(0).id());
        assertEquals(TransactionType.INCOME, transactions.get(0).type());
        assertEquals(Money.ofCents(500000), transactions.get(0).amount());
        assertEquals(2, transactions.get(1).id());
        assertEquals("groceries", transactions.get(1).category());
    }

    @Test
    void parsesCrlfLineEndingsAndUtf8Bom() {
        String content = "\uFEFF" + CsvCodec.HEADER + "\r\n"
                + "1,2026-11-01,expense,42.50,groceries,Countdown\r\n";

        List<Transaction> transactions = codec.parse(content);

        assertEquals(1, transactions.size());
        assertEquals("Countdown", transactions.get(0).description());
    }

    @Test
    void ignoresBlankLines() {
        String content = CsvCodec.HEADER + "\n\n"
                + "1,2026-11-01,expense,42.50,groceries,Countdown\n\n";

        assertEquals(1, codec.parse(content).size());
    }

    @Test
    void parsesQuotedCommasAndEscapedQuotes() {
        String content = CsvCodec.HEADER + "\n"
                + "1,2026-11-01,expense,42.50,groceries,\"Countdown, Auckland\"\n"
                + "2,2026-11-02,expense,10.00,dining,\"He said \"\"hi\"\"\"\n";

        List<Transaction> transactions = codec.parse(content);

        assertEquals("Countdown, Auckland", transactions.get(0).description());
        assertEquals("He said \"hi\"", transactions.get(1).description());
    }

    @Test
    void parsesDescriptionContainingNewlineAndKeepsLaterLineNumbersAccurate() {
        String content = CsvCodec.HEADER + "\n"
                + "1,2026-11-01,expense,42.50,groceries,\"line1\nline2\"\n"
                + "2,2026-11-02,expense,not-an-amount,groceries,Broken\n";

        StorageException e = failure(content);

        assertTrue(e.getMessage().startsWith("4: "));
        assertTrue(e.getMessage().contains("invalid amount"));
    }

    @Test
    void roundTripsCommaQuoteUnicodeAndNewline() {
        Transaction original = new Transaction(7, LocalDate.of(2026, 11, 3), TransactionType.EXPENSE,
                Money.ofCents(123456), "eating, out", "Café, \"the best\"\nand cheapest");

        String content = CsvCodec.HEADER + "\n" + codec.encode(original) + "\n";
        Transaction parsed = codec.parse(content).get(0);

        assertEquals(original, parsed);
    }

    // ---------------------------------------------------------------- failures

    @Test
    void rejectsWrongHeader() {
        StorageException e = failure("id,date,type,amount,description\n");

        assertTrue(e.getMessage().startsWith("1: "));
        assertTrue(e.getMessage().contains("expected header"));
    }

    @Test
    void rejectsWrongColumnCount() {
        StorageException e = failure(CsvCodec.HEADER + "\n1,2026-11-01,expense,42.50,groceries\n");

        assertTrue(e.getMessage().startsWith("2: "));
        assertTrue(e.getMessage().endsWith("expected 6 columns, got 5"));
    }

    @Test
    void rejectsInvalidId() {
        StorageException e = failure(CsvCodec.HEADER + "\nabc,2026-11-01,expense,42.50,groceries,Shop\n");

        assertTrue(e.getMessage().startsWith("2: "));
        assertTrue(e.getMessage().contains("invalid id"));
    }

    @Test
    void rejectsNonPositiveId() {
        StorageException e = failure(CsvCodec.HEADER + "\n0,2026-11-01,expense,42.50,groceries,Shop\n");

        assertTrue(e.getMessage().contains("must be positive"));
    }

    @Test
    void rejectsInvalidDate() {
        StorageException e = failure(CsvCodec.HEADER + "\n1,2026-13-01,expense,42.50,groceries,Shop\n");

        assertTrue(e.getMessage().startsWith("2: "));
        assertTrue(e.getMessage().contains("invalid date '2026-13-01'"));
    }

    @Test
    void rejectsInvalidAmount() {
        StorageException e = failure(CsvCodec.HEADER + "\n1,2026-11-01,expense,42.505,groceries,Shop\n");

        assertTrue(e.getMessage().startsWith("2: "));
        assertTrue(e.getMessage().contains("invalid amount"));
    }

    @Test
    void rejectsUnknownType() {
        StorageException e = failure(CsvCodec.HEADER + "\n1,2026-11-01,transfer,42.50,groceries,Shop\n");

        assertTrue(e.getMessage().contains("unknown transaction type"));
    }

    @Test
    void rejectsDuplicateId() {
        String content = CsvCodec.HEADER + "\n"
                + "1,2026-11-01,expense,42.50,groceries,One\n"
                + "1,2026-11-02,expense,10.00,groceries,Two\n";

        StorageException e = failure(content);

        assertTrue(e.getMessage().startsWith("3: "));
        assertTrue(e.getMessage().endsWith("duplicate id 1"));
    }

    @Test
    void rejectsEmptyDescription() {
        StorageException e = failure(CsvCodec.HEADER + "\n1,2026-11-01,expense,42.50,groceries,\n");

        assertTrue(e.getMessage().contains("description is required"));
    }

    @Test
    void rejectsUnterminatedQuote() {
        StorageException e = failure(CsvCodec.HEADER + "\n1,2026-11-01,expense,42.50,groceries,\"unterminated\n");

        assertTrue(e.getMessage().contains("unterminated quoted field"));
    }

    @Test
    void defaultsBlankCategoryToUncategorized() {
        String content = CsvCodec.HEADER + "\n1,2026-11-01,expense,42.50,,Shop\n";

        assertEquals(Category.UNCATEGORIZED, codec.parse(content).get(0).category());
    }
}
