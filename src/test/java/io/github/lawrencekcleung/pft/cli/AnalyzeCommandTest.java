package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.store.CsvCodec;
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

class AnalyzeCommandTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

    private static final String UNDER_BUDGET = CsvCodec.HEADER + "\n"
            + "1,2026-11-01,income,5000.00,income,Salary Oct\n"
            + "2,2026-11-01,expense,42.50,groceries,\"Countdown, Auckland\"\n"
            + "3,2026-11-02,expense,15.80,transport,Uber to airport\n";

    private static final String OVER_BUDGET = CsvCodec.HEADER + "\n"
            + "1,2026-11-01,expense,42.50,groceries,\"Countdown, Auckland\"\n"
            + "2,2026-11-02,expense,1200.00,rent,November rent\n";

    private static final String EXPECTED_OVER = "[budget-exceeded] 2026-11 expenses total "
            + "1242.50, above the 500.00 monthly budget\n";

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

    private void writeStore(String content) throws IOException {
        Files.createDirectories(data().getParent());
        Files.writeString(data(), content, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void writeUnderBudgetStore() throws IOException {
        writeStore(UNDER_BUDGET);
    }

    @Test
    void noFindingsWhenTheMonthIsUnderBudget() {
        assertEquals(0, cli().run("analyze"));

        assertEquals("No findings.\n", stdout());
    }

    @Test
    void theBudgetRuleFlagsAnOverBudgetMonth() throws IOException {
        writeStore(OVER_BUDGET);

        assertEquals(0, cli().run("analyze"));

        assertEquals(EXPECTED_OVER, stdout());
    }

    @Test
    void theRuleFilterSelectsTheBudgetRule() throws IOException {
        writeStore(OVER_BUDGET);

        assertEquals(0, cli().run("analyze", "--rule", "budget-exceeded"));

        assertEquals(EXPECTED_OVER, stdout());
    }

    @Test
    void theConfigFileOverridesTheBudget() throws IOException {
        Path config = dir.resolve("pft.properties");
        Files.writeString(config, "monthly.budget=10.00\n", StandardCharsets.UTF_8);

        assertEquals(0, cli().run("analyze", "--config", config.toString()));

        assertEquals("[budget-exceeded] 2026-11 expenses total 58.30, above the 10.00 "
                + "monthly budget\n", stdout());
    }

    @Test
    void theMonthFilterExcludesOtherMonths() throws IOException {
        writeStore(OVER_BUDGET);

        assertEquals(0, cli().run("analyze", "--month", "2026-12"));

        assertEquals("No findings.\n", stdout());
    }

    @Test
    void unknownRuleIsAUsageError() {
        assertEquals(1, cli().run("analyze", "--rule", "nope"));

        assertEquals("unknown rule 'nope'; available: budget-exceeded\n", stderr());
        assertTrue(stdout().isEmpty());
    }

    @Test
    void theRemovedDuplicateChargeRuleIsNoLongerRegistered() {
        assertEquals(1, cli().run("analyze", "--rule", "duplicate-charge"));

        assertEquals("unknown rule 'duplicate-charge'; available: budget-exceeded\n", stderr());
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
