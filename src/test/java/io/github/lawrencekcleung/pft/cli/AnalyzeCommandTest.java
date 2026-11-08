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

class AnalyzeCommandTest {

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

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    @BeforeEach
    void writeFixture() throws IOException {
        Files.createDirectories(data().getParent());
        Files.writeString(data(), FIXTURE, StandardCharsets.UTF_8);
    }

    @Test
    void printsTheCanonicalFinding() {
        assertEquals(0, cli().run("analyze"));

        assertEquals("[duplicate-charge] #8, #9  9.90 \"Coffee\" on 2026-11-06 and "
                + "2026-11-07 (1 day apart)\n", stdout());
    }

    @Test
    void theRuleFilterNarrowsTheRun() {
        assertEquals(0, cli().run("analyze", "--rule", "duplicate-charge"));

        assertTrue(stdout().startsWith("[duplicate-charge] #8, #9"));
    }

    @Test
    void unknownRuleIsAUsageError() {
        assertEquals(1, cli().run("analyze", "--rule", "nope"));

        assertEquals("unknown rule 'nope'; available: duplicate-charge\n", stderr());
        assertTrue(stdout().isEmpty());
    }

    @Test
    void noFindingsPrintsTheMessageAndSucceeds() throws IOException {
        Files.writeString(data(), CsvCodec.HEADER + "\n"
                + "1,2026-11-01,income,5000.00,income,Salary Oct\n", StandardCharsets.UTF_8);

        assertEquals(0, cli().run("analyze"));

        assertEquals("No findings.\n", stdout());
    }

    @Test
    void monthFilterExcludesPairsOutsideTheMonth() {
        assertEquals(0, cli().run("analyze", "--month", "2026-12"));

        assertEquals("No findings.\n", stdout());
    }

    @Test
    void incomeDuplicatesAreNotFlagged() throws IOException {
        Files.writeString(data(), CsvCodec.HEADER + "\n"
                + "1,2026-11-01,income,5000.00,income,Salary Oct\n"
                + "2,2026-11-02,income,5000.00,income,Salary Oct\n", StandardCharsets.UTF_8);

        assertEquals(0, cli().run("analyze"));

        assertEquals("No findings.\n", stdout());
    }

    @Test
    void analyzeOnAMissingStoreCreatesNothing() throws IOException {
        Files.delete(data());
        Files.delete(data().getParent());

        assertEquals(0, cli().run("analyze"));

        assertEquals("No findings.\n", stdout());
        assertFalse(Files.exists(data()));
        assertFalse(Files.exists(data().getParent()));
    }

    @Test
    void analyzeDoesNotModifyTheFile() throws IOException {
        String before = Files.readString(data(), StandardCharsets.UTF_8);

        cli().run("analyze");

        assertEquals(before, Files.readString(data(), StandardCharsets.UTF_8));
    }
}
