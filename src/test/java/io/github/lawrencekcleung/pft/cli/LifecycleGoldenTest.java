package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.store.CsvCodec;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the PRD §7.6 walkthrough end to end and asserts the final CSV and summary byte for byte.
 *
 * <p>The two Coffee rows are added with {@code --category dining}, matching the categories shown in
 * the PRD's final file; everything else follows the transcript command for command.</p>
 */
class LifecycleGoldenTest {

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

    private Path importFile() {
        return dir.resolve("nov.csv");
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

    private String csv() throws IOException {
        return Files.readString(data(), StandardCharsets.UTF_8);
    }

    @Test
    void reproducesThePrdWalkthrough() throws IOException {
        assertEquals(0, run("add", "--date", "2026-11-01", "--type", "income",
                "--amount", "5000", "--description", "Salary Oct", "--category", "income"));
        assertEquals("Added #1 (category: income)\n", stdout());

        assertEquals(0, run("add", "--date", "2026-11-01", "--type", "expense",
                "--amount", "42.50", "--description", "Countdown, Auckland"));
        assertEquals("Added #2 (category: UNCATEGORIZED)\n", stdout());

        assertEquals(0, run("add", "--date", "2026-11-02", "--type", "expense",
                "--amount", "1200.00", "--description", "November rent"));
        assertEquals("Added #3 (category: UNCATEGORIZED)\n", stdout());

        assertEquals(0, run("analyze"));
        assertEquals("[budget-exceeded] 2026-11 expenses total 1242.50, above the "
                + "500.00 monthly budget\n", stdout());

        Files.writeString(importFile(), """
                date,type,amount,description
                2026-11-02,expense,15.80,Uber to airport
                2026-11-03,expense,68.40,Countdown weekly shop
                2026-11-04,expense,12.00,Spotify
                2026-11-06,expense,26.10,"Lunch, Ponsonby"
                2026-11-07,expense,abc,Broken
                """, StandardCharsets.UTF_8);

        assertEquals(2, run("import", "--file", importFile().toString()));
        assertEquals("Imported 4 transactions.\n", stdout());
        assertTrue(stderr().contains(importFile() + ":6: invalid amount 'abc'"));
        assertTrue(stderr().contains("Skipped 1 row."));

        String beforeDryRun = csv();
        assertEquals(0, run("categorize"));
        assertEquals(beforeDryRun, csv(), "a dry run must not write");
        assertTrue(stdout().endsWith(
                "Dry run: 6 transactions would be categorized. Use --apply to write.\n"));

        assertEquals(0, run("categorize", "--apply"));
        assertTrue(stdout().endsWith("Categorized 6 transactions.\n"));

        assertEquals(0, run("categorize", "--id", "7",
                "--category", "client-entertainment"));
        assertEquals("Categorized #7 as client-entertainment.\n", stdout());

        assertEquals(0, run("delete", "--id", "3"));
        assertEquals("Deleted #3.\n", stdout());

        assertEquals(0, run("add", "--date", "2026-11-06", "--type", "expense",
                "--amount", "9.90", "--description", "Coffee", "--category", "dining"));
        assertEquals("Added #8 (category: dining)\n", stdout());

        assertEquals(0, run("add", "--date", "2026-11-07", "--type", "expense",
                "--amount", "9.90", "--description", "Coffee", "--category", "dining"));
        assertEquals("Added #9 (category: dining)\n", stdout());

        // The analysis framework is in place; no built-in rule is registered yet.
        assertEquals(0, run("analyze"));
        assertEquals("No findings.\n", stdout());

        assertEquals(0, run("summary", "--month", "2026-11"));
        assertEquals("""
                Period: 2026-11   Income: 5000.00   Expenses: 184.60   Net: +4815.40

                Category                     Spend     Share
                groceries                   110.90     60.1%
                client-entertainment         26.10     14.1%
                dining                       19.80     10.7%
                transport                    15.80      8.6%
                subscriptions                12.00      6.5%
                """, stdout());

        assertEquals(CsvCodec.HEADER + "\n"
                + "1,2026-11-01,income,5000.00,income,Salary Oct\n"
                + "2,2026-11-01,expense,42.50,groceries,\"Countdown, Auckland\"\n"
                + "4,2026-11-02,expense,15.80,transport,Uber to airport\n"
                + "5,2026-11-03,expense,68.40,groceries,Countdown weekly shop\n"
                + "6,2026-11-04,expense,12.00,subscriptions,Spotify\n"
                + "7,2026-11-06,expense,26.10,client-entertainment,\"Lunch, Ponsonby\"\n"
                + "8,2026-11-06,expense,9.90,dining,Coffee\n"
                + "9,2026-11-07,expense,9.90,dining,Coffee\n", csv());
    }
}
