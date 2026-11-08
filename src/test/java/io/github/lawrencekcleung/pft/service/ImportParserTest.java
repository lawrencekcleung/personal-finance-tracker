package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Category;
import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportParserTest {

    private static ParsedImport parse(String... lines) {
        return new ImportParser().parse(String.join("\n", lines) + "\n");
    }

    // ------------------------------------------------------------------ happy

    @Test
    void parsesRowsWithoutAHeader() {
        ParsedImport parsed = parse(
                "2026-11-01,expense,42.50,Countdown",
                "2026-11-01,income,5000,Salary Oct");

        assertEquals(2, parsed.drafts().size());
        assertTrue(parsed.errors().isEmpty());

        Transaction first = parsed.drafts().get(0);
        assertEquals(0, first.id(), "ids are assigned by the store, not read from the file");
        assertEquals(LocalDate.of(2026, 11, 1), first.date());
        assertEquals(TransactionType.EXPENSE, first.type());
        assertEquals(Money.ofCents(4250), first.amount());
        assertEquals("Countdown", first.description());
        assertEquals(Category.UNCATEGORIZED, first.category());
    }

    @Test
    void parsesAHeaderAndTheOptionalCategory() {
        ParsedImport parsed = parse(
                "date,type,amount,description,category",
                "2026-11-01,expense,42.50,\"Countdown, Auckland\",groceries");

        assertEquals(1, parsed.drafts().size());
        Transaction stored = parsed.drafts().get(0);
        assertEquals("Countdown, Auckland", stored.description());
        assertEquals("groceries", stored.category());
    }

    @Test
    void headerDetectionIsCaseInsensitive() {
        ParsedImport parsed = parse("DATE,type,amount,description",
                "2026-11-01,expense,1.00,Shop");

        assertEquals(1, parsed.drafts().size());
        assertTrue(parsed.errors().isEmpty());
    }

    @Test
    void aDateInTheFirstFieldMeansDataNotAHeader() {
        ParsedImport parsed = parse("2026-11-01,expense,42.50,Countdown");

        assertEquals(1, parsed.drafts().size());
        assertTrue(parsed.errors().isEmpty());
    }

    @Test
    void ignoresALeadingIdColumn() {
        ParsedImport parsed = parse(
                "id,date,type,amount,description,category",
                "99,2026-11-01,expense,42.50,Shop,groceries");

        assertEquals(1, parsed.drafts().size());
        assertTrue(parsed.errors().isEmpty());
        Transaction stored = parsed.drafts().get(0);
        assertEquals(0, stored.id(), "the source id is ignored");
        assertEquals("Shop", stored.description());
        assertEquals("groceries", stored.category());
    }

    @Test
    void fiveColumnsStartingWithADateUseTheOptionalCategory() {
        ParsedImport parsed = parse("2026-11-01,expense,42.50,Shop,groceries");

        assertEquals("groceries", parsed.drafts().get(0).category());
    }

    @Test
    void skipsBlankLinesButKeepsSourceLineNumbers() {
        ParsedImport parsed = new ImportParser().parse(
                "date,type,amount,description\n"
                        + "\n"
                        + "2026-11-01,expense,1.00,One\n"
                        + "\n"
                        + "bad-date,expense,1.00,Two\n");

        assertEquals(1, parsed.drafts().size());
        assertEquals(1, parsed.errors().size());
        assertEquals(5, parsed.errors().get(0).lineNumber());
    }

    @Test
    void handlesCrlfAndAByteOrderMark() {
        ParsedImport parsed = new ImportParser().parse(
                "\uFEFFdate,type,amount,description\r\n"
                        + "2026-11-01,expense,42.50,Countdown\r\n");

        assertEquals(1, parsed.drafts().size());
        assertEquals("Countdown", parsed.drafts().get(0).description());
    }

    @Test
    void nullOrEmptyContentHasNoRows() {
        assertTrue(new ImportParser().parse(null).drafts().isEmpty());
        assertTrue(new ImportParser().parse("").drafts().isEmpty());
    }

    // --------------------------------------------------------------- failures

    @Test
    void invalidDateIsReportedWithItsLine() {
        ParsedImport parsed = parse(
                "2026-11-01,expense,1.00,Good",
                "2026-13-01,expense,1.00,Bad");

        assertEquals(1, parsed.drafts().size());
        ImportError error = parsed.errors().get(0);
        assertEquals(2, error.lineNumber());
        assertEquals("invalid date '2026-13-01': expected yyyy-MM-dd", error.message());
    }

    @Test
    void unknownTypeIsReported() {
        ImportError error = parse("2026-11-01,transfer,1.00,Move").errors().get(0);

        assertEquals("unknown transaction type: transfer", error.message());
    }

    @Test
    void invalidAmountIsReported() {
        ImportError error = parse("2026-11-01,expense,abc,Shop").errors().get(0);

        assertEquals("invalid amount 'abc': expected a non-negative number with at most "
                + "2 decimal places", error.message());
    }

    @Test
    void wrongColumnCountIsReported() {
        ParsedImport tooFew = parse("2026-11-01,expense,1.00");
        ParsedImport tooMany = parse("2026-11-01,expense,1.00,Shop,groceries,extra");

        assertEquals("expected 4 or 5 columns, got 3", tooFew.errors().get(0).message());
        assertEquals("expected 4 or 5 columns, got 6", tooMany.errors().get(0).message());
    }

    @Test
    void blankDescriptionIsReported() {
        ImportError error = parse("2026-11-01,expense,1.00,   ").errors().get(0);

        assertEquals("description is required", error.message());
    }

    @Test
    void unterminatedQuoteIsReportedWithoutLosingOtherRows() {
        ParsedImport parsed = parse(
                "2026-11-01,expense,1.00,Good",
                "2026-11-02,expense,1.00,\"Broken",
                "2026-11-03,expense,1.00,Also good");

        assertEquals(List.of("Good", "Also good"),
                parsed.drafts().stream().map(Transaction::description).toList());
        assertEquals(2, parsed.errors().get(0).lineNumber());
        assertEquals("unterminated quoted field", parsed.errors().get(0).message());
    }

    @Test
    void aMalformedFirstLineIsAnErrorNotAHeader() {
        ParsedImport parsed = parse("notadate,expense,1.00,Shop");

        assertTrue(parsed.drafts().isEmpty());
        assertEquals(1, parsed.errors().get(0).lineNumber());
        assertTrue(parsed.errors().get(0).message().contains("invalid date 'notadate'"));
    }
}
