package io.github.lawrencekcleung.pft.cli;

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

/** End-to-end workflows across several commands against one real CSV store. */
class CliIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

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

    private int run(String... args) {
        outBytes.reset();
        errBytes.reset();
        return cli().run(args);
    }

    private String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private void add(String date, String type, String amount, String description,
                     String category) {
        assertEquals(0, run("add", "--date", date, "--type", type, "--amount", amount,
                "--description", description, "--category", category));
    }

    private String csv() throws IOException {
        return Files.readString(data(), StandardCharsets.UTF_8);
    }

    @Test
    void addThenListAndSummary() {
        add("2026-11-01", "income", "5000.00", "Salary Oct", "income");
        add("2026-11-01", "expense", "42.50", "Countdown", "groceries");

        assertEquals(0, run("list"));
        assertTrue(stdout().contains("Salary Oct"));
        assertTrue(stdout().contains("Countdown"));

        assertEquals(0, run("summary", "--month", "2026-11"));
        assertTrue(stdout().contains(
                "Income: 5000.00   Expenses: 42.50   Net: +4957.50"));
    }

    @Test
    void importThenCategorizeThenList() throws IOException {
        Path importFile = dir.resolve("nov.csv");
        Files.writeString(importFile, """
                date,type,amount,description
                2026-11-02,expense,15.80,Uber to airport
                2026-11-04,expense,12.00,Spotify
                """, StandardCharsets.UTF_8);

        assertEquals(0, run("import", "--file", importFile.toString()));
        assertEquals(0, run("categorize", "--apply"));
        assertTrue(stdout().endsWith("Categorized 2 transactions.\n"));

        assertEquals(0, run("list"));
        assertTrue(stdout().contains("transport"));
        assertTrue(stdout().contains("subscriptions"));
    }

    @Test
    void analyzeFindsDuplicateCharges() {
        add("2026-11-06", "expense", "9.90", "Coffee", "dining");
        add("2026-11-07", "expense", "9.90", "Coffee", "dining");

        assertEquals(0, run("analyze"));
        assertEquals("[duplicate-charge] #1, #2  9.90 \"Coffee\" on 2026-11-06 and "
                + "2026-11-07 (1 day apart)\n", stdout());
    }

    @Test
    void deleteRemovesTheRowFromList() {
        add("2026-11-01", "income", "1.00", "One", "income");
        add("2026-11-02", "expense", "2.00", "Two", "dining");

        assertEquals(0, run("delete", "--id", "1"));
        assertEquals("Deleted #1.\n", stdout());

        assertEquals(0, run("list"));
        assertFalse(stdout().contains("One"));
        assertTrue(stdout().contains("Two"));
    }

    @Test
    void dryRunCategorizeLeavesTheFileByteIdentical() throws IOException {
        assertEquals(0, run("add", "--date", "2026-11-06", "--type", "expense",
                "--amount", "9.90", "--description", "Coffee"));
        String before = csv();

        assertEquals(0, run("categorize"));

        assertTrue(stdout().contains("UNCATEGORIZED -> dining"));
        assertEquals(before, csv());
    }

    @Test
    void rootHelpListsEveryShippedCommand() {
        assertEquals(0, run("--help"));

        for (String command : new String[] {"add", "import", "list", "categorize",
                "summary", "analyze", "delete"}) {
            assertTrue(stdout().contains(command), () -> "help is missing " + command);
        }
    }

    @Test
    void unknownCommandAndUnknownOptionAreUsageErrors() {
        assertEquals(1, run("nope"));
        assertTrue(stderr().contains("nope"));

        assertEquals(1, run("add", "--date", "2026-11-01", "--type", "expense",
                "--amount", "1.00", "--description", "X", "--nope"));
        assertTrue(stderr().contains("Unknown option"));
        assertTrue(stderr().contains("--nope"));
    }

    @Test
    void monthFiltersAreConsistentAcrossListAndSummary() {
        add("2026-11-01", "expense", "10.00", "November", "dining");
        add("2026-12-01", "expense", "1.00", "December", "dining");

        assertEquals(0, run("list", "--month", "2026-12"));
        assertTrue(stdout().contains("December"));
        assertFalse(stdout().contains("November"));

        assertEquals(0, run("summary", "--month", "2026-12"));
        assertTrue(stdout().contains("Expenses: 1.00"));
    }
}
