package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.store.CsvCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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

class SummaryCommandTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

    private static final String FIXTURE = CsvCodec.HEADER + "\n"
            + "1,2026-11-01,income,5000.00,income,Salary Oct\n"
            + "2,2026-11-01,expense,42.50,groceries,\"Countdown, Auckland\"\n"
            + "4,2026-11-02,expense,15.80,transport,Uber to airport\n"
            + "5,2026-11-03,expense,68.40,groceries,Countdown weekly shop\n"
            + "6,2026-11-04,expense,12.00,subscriptions,Spotify\n"
            + "7,2026-11-06,expense,26.10,client-entertainment,\"Lunch, Ponsonby\"\n"
            + "8,2026-11-06,expense,9.90,dining,Coffee\n"
            + "9,2026-11-07,expense,9.90,dining,Coffee\n";

    @TempDir
    Path dir;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    private Path data() {
        return dir.resolve("data/transactions.csv");
    }

    private Cli cli() {
        return new Cli(CommandRegistry.defaults(), out, err, CLOCK, Map.of(), data());
    }

    private String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    @BeforeEach
    void writeFixture() throws IOException {
        Files.createDirectories(data().getParent());
        Files.writeString(data(), FIXTURE, StandardCharsets.UTF_8);
    }

    @Test
    void monthSummaryMatchesTheHandCheckedTotal() {
        assertEquals(0, cli().run("summary", "--month", "2026-11"));

        assertEquals("""
                Period: 2026-11   Income: 5000.00   Expenses: 184.60   Net: +4815.40

                Category                     Spend     Share
                groceries                   110.90     60.1%
                client-entertainment         26.10     14.1%
                dining                       19.80     10.7%
                transport                    15.80      8.6%
                subscriptions                12.00      6.5%
                """, stdout());
    }

    @Test
    void allTimeIsTheDefault() throws IOException {
        Files.writeString(data(), FIXTURE
                + "10,2026-12-01,expense,1.00,dining,December\n", StandardCharsets.UTF_8);

        assertEquals(0, cli().run("summary"));

        assertTrue(stdout().startsWith("Period: all time   Income: 5000.00   "
                + "Expenses: 185.60   Net: +4814.40"));
    }

    @Test
    void anEmptyMonthPrintsTheEmptyState() {
        assertEquals(0, cli().run("summary", "--month", "2026-12"));

        assertEquals("No transactions.\n", stdout());
    }

    @Test
    void summaryOnAMissingStoreCreatesNothing() throws IOException {
        Files.delete(data());
        Files.delete(data().getParent());

        assertEquals(0, cli().run("summary"));

        assertEquals("No transactions.\n", stdout());
        assertFalse(Files.exists(data()));
        assertFalse(Files.exists(data().getParent()));
    }

    @Test
    void summaryDoesNotModifyTheFile() throws IOException {
        String before = Files.readString(data(), StandardCharsets.UTF_8);

        cli().run("summary");

        assertEquals(before, Files.readString(data(), StandardCharsets.UTF_8));
    }
}
