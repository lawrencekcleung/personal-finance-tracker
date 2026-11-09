package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.store.CsvCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
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

class AddCommandTest {

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

    private String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void addWritesOneRowAndPrintsTheAssignedId() throws Exception {
        int exit = cli().run("add",
                "--date", "2026-11-01",
                "--type", "expense",
                "--amount", "42.50",
                "--description", "Countdown",
                "--category", "groceries");

        assertEquals(0, exit);
        assertEquals("Added #1 (category: groceries)\n", stdout());
        assertEquals(CsvCodec.HEADER + "\n"
                        + "1,2026-11-01,expense,42.50,groceries,Countdown\n",
                Files.readString(data(), StandardCharsets.UTF_8));
    }

    @Test
    void addWithoutCategoryPrintsTheEffectiveDefault() throws Exception {
        assertEquals(0, cli().run("add",
                "--date=2026-11-01", "--type=expense", "--amount=9.90",
                "--description=Coffee"));

        assertEquals("Added #1 (category: UNCATEGORIZED)\n", stdout());
        assertTrue(Files.readString(data(), StandardCharsets.UTF_8)
                .contains(",UNCATEGORIZED,Coffee"));
    }

    @Test
    void addHelpDoesNotRequireTheOptions() {
        assertEquals(0, cli().run("add", "--help"));

        assertTrue(stdout().contains("Usage: pft add"));
        assertTrue(stdout().contains("--description"));
        assertTrue(stderr().isEmpty());
    }

    @Test
    void missingRequiredOptionIsAUsageErrorAndWritesNothing() {
        int exit = cli().run("add",
                "--date", "2026-11-01", "--type", "expense", "--amount", "42.50");

        assertEquals(1, exit);
        assertTrue(stderr().contains("Missing required option"));
        assertTrue(stderr().contains("--description"));
        assertFalse(Files.exists(data()));
    }

    @Test
    void malformedAmountIsAUsageError() {
        int exit = cli().run("add",
                "--date", "2026-11-01", "--type", "expense",
                "--amount", "42.505", "--description", "Shop");

        assertEquals(1, exit);
        assertTrue(stderr().contains("Invalid value for option '--amount'"));
        assertTrue(stderr().contains("invalid amount '42.505'"));
        assertFalse(Files.exists(data()));
    }

    @Test
    void malformedDateIsAUsageError() {
        int exit = cli().run("add",
                "--date", "2026-13-01", "--type", "expense",
                "--amount", "1.00", "--description", "Shop");

        assertEquals(1, exit);
        assertTrue(stderr().contains("Invalid value for option '--date'"));
        assertFalse(Files.exists(data()));
    }

    @Test
    void unknownTypeIsADataError() {
        int exit = cli().run("add",
                "--date", "2026-11-01", "--type", "transfer",
                "--amount", "1.00", "--description", "Move");

        assertEquals(2, exit);
        assertTrue(stderr().contains("unknown transaction type: transfer"));
        assertFalse(Files.exists(data()));
    }

    @Test
    void blankDescriptionIsADataError() {
        int exit = cli().run("add",
                "--date", "2026-11-01", "--type", "expense",
                "--amount", "1.00", "--description", "   ");

        assertEquals(2, exit);
        assertTrue(stderr().contains("description is required"));
        assertFalse(Files.exists(data()));
    }
}
