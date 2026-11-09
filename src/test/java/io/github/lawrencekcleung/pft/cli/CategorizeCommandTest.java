package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.model.Category;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.store.CsvCodec;
import io.github.lawrencekcleung.pft.store.CsvTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategorizeCommandTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

    private static final String FIXTURE = CsvCodec.HEADER + "\n"
            + "1,2026-11-01,expense,42.50,groceries,Countdown\n"
            + "2,2026-11-02,expense,15.80,,Uber to airport\n"
            + "3,2026-11-03,expense,12.00,,Spotify\n"
            + "4,2026-11-04,expense,7.00,,Mystery shop\n"
            + "5,2026-11-05,income,5000.00,,Salary\n";

    @TempDir
    Path dir;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintWriter out = new PrintWriter(outBytes, true, StandardCharsets.UTF_8);
    private final PrintWriter err = new PrintWriter(errBytes, true, StandardCharsets.UTF_8);

    private Path data() {
        return dir.resolve("data/transactions.csv");
    }

    private Cli cli() {
        return Cli.create(CLOCK, Map.of(), data(), out, err);
    }

    private String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private String csv() throws IOException {
        return Files.readString(data(), StandardCharsets.UTF_8);
    }

    private Transaction find(int id) {
        return new CsvTransactionRepository(data()).findById(id).orElseThrow();
    }

    @BeforeEach
    void writeFixture() throws IOException {
        Files.createDirectories(data().getParent());
        Files.writeString(data(), FIXTURE, StandardCharsets.UTF_8);
    }

    // --------------------------------------------------------------- dry run

    @Test
    void dryRunPrintsThePlanAndWritesNothing() throws IOException {
        String before = csv();

        assertEquals(0, cli().run("categorize"));

        assertEquals("""
                #2  UNCATEGORIZED -> transport  "Uber to airport"
                #3  UNCATEGORIZED -> subscriptions  "Spotify"
                Dry run: 2 transactions would be categorized. Use --apply to write.
                """, stdout());
        assertEquals(before, csv());
        assertFalse(Files.exists(data().resolveSibling("transactions.csv.seq")));
    }

    @Test
    void dryRunWithNothingToDoPrintsTheEmptyMessage() throws IOException {
        cli().run("categorize", "--apply");
        String afterApply = csv();
        outBytes.reset();

        assertEquals(0, cli().run("categorize"));

        assertEquals("No uncategorized transactions.\n", stdout());
        assertEquals(afterApply, csv());
    }

    // ----------------------------------------------------------------- apply

    @Test
    void applyWritesThePlannedChanges() throws IOException {
        assertEquals(0, cli().run("categorize", "--apply"));

        assertEquals("""
                #2  UNCATEGORIZED -> transport  "Uber to airport"
                #3  UNCATEGORIZED -> subscriptions  "Spotify"
                Categorized 2 transactions.
                """, stdout());
        assertEquals("transport", find(2).category());
        assertEquals("subscriptions", find(3).category());
        assertEquals(Category.UNCATEGORIZED, find(4).category());
        assertEquals("groceries", find(1).category());
    }

    @Test
    void aSecondApplyIsANoOpAndDoesNotRewriteTheFile() throws IOException {
        cli().run("categorize", "--apply");
        String afterFirst = csv();
        outBytes.reset();

        assertEquals(0, cli().run("categorize", "--apply"));

        assertEquals("No uncategorized transactions.\n", stdout());
        assertEquals(afterFirst, csv());
    }

    // -------------------------------------------------------------- override

    @Test
    void manualOverrideWritesImmediately() {
        assertEquals(0, cli().run("categorize", "--id", "4",
                "--category", "client-entertainment"));

        assertEquals("Categorized #4 as client-entertainment.\n", stdout());
        assertEquals("client-entertainment", find(4).category());
    }

    @Test
    void aManualOverrideSurvivesALaterApply() {
        cli().run("categorize", "--id", "4", "--category", "client-entertainment");
        outBytes.reset();

        cli().run("categorize", "--apply");

        assertEquals("client-entertainment", find(4).category());
        assertEquals("transport", find(2).category());
    }

    @Test
    void overrideAndApplyInOneInvocationAssignsTheOverrideFirst() {
        assertEquals(0, cli().run("categorize", "--id", "4", "--category", "dining", "--apply"));

        assertTrue(stdout().startsWith("Categorized #4 as dining.\n"));
        assertTrue(stdout().contains("Categorized 2 transactions.\n"));
        assertEquals("dining", find(4).category());
        assertEquals("transport", find(2).category());
    }

    @Test
    void unknownIdIsADataErrorAndWritesNothing() throws IOException {
        String before = csv();

        assertEquals(2, cli().run("categorize", "--id", "99", "--category", "dining"));

        assertEquals("unknown transaction id: 99\n", stderr());
        assertEquals(before, csv());
    }

    @Test
    void idWithoutCategoryIsAUsageError() {
        assertEquals(1, cli().run("categorize", "--id", "4"));

        assertEquals("--id requires --category\n", stderr());
        assertTrue(stdout().isEmpty());
    }

    @Test
    void categoryWithoutIdIsAUsageError() {
        assertEquals(1, cli().run("categorize", "--category", "dining"));

        assertEquals("--category requires --id\n", stderr());
        assertTrue(stdout().isEmpty());
    }
}
