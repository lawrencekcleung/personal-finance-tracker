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

class ListCommandTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

    private static final String FIXTURE = CsvCodec.HEADER + "\n"
            + "1,2026-11-02,expense,10.00,dining,Two\n"
            + "2,2026-11-01,expense,42.50,groceries,\"Countdown, Auckland\"\n"
            + "3,2026-11-01,income,5000.00,income,Salary Oct\n"
            + "4,2026-11-03,expense,5.00,,Mystery\n"
            + "5,2026-12-01,expense,1.00,dining,December\n";

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
    void listsInDateThenIdOrderAsATable() {
        assertEquals(0, cli().run("list"));

        assertEquals("""
                ID  Date        Type      Amount  Category       Description
                 2  2026-11-01  expense    42.50  groceries      Countdown, Auckland
                 3  2026-11-01  income   5000.00  income         Salary Oct
                 1  2026-11-02  expense    10.00  dining         Two
                 4  2026-11-03  expense     5.00  UNCATEGORIZED  Mystery
                 5  2026-12-01  expense     1.00  dining         December
                """, stdout());
    }

    @Test
    void filtersByMonth() {
        assertEquals(0, cli().run("list", "--month", "2026-12"));

        String output = stdout();
        assertTrue(output.contains("December"));
        assertFalse(output.contains("Countdown"));
        assertEquals(2, output.stripTrailing().split("\n").length);
    }

    @Test
    void filtersByCategory() {
        assertEquals(0, cli().run("list", "--category", "groceries"));

        String output = stdout();
        assertTrue(output.contains("Countdown, Auckland"));
        assertFalse(output.contains("Salary Oct"));
        assertEquals(2, output.stripTrailing().split("\n").length);
    }

    @Test
    void filtersUncategorized() {
        assertEquals(0, cli().run("list", "--uncategorized"));

        String output = stdout();
        assertTrue(output.contains("Mystery"));
        assertFalse(output.contains("groceries"));
        assertEquals(2, output.stripTrailing().split("\n").length);
    }

    @Test
    void combinesMonthAndCategoryFilters() {
        assertEquals(0, cli().run("list", "--month", "2026-11", "--category", "dining"));

        String output = stdout();
        assertTrue(output.contains("Two"));
        assertFalse(output.contains("December"));
    }

    @Test
    void emptyResultPrintsOneLine() {
        assertEquals(0, cli().run("list", "--category", "transport"));

        assertEquals("No transactions.\n", stdout());
    }

    @Test
    void categoryAndUncategorizedTogetherAreAUsageError() {
        assertEquals(1, cli().run("list", "--category", "dining", "--uncategorized"));

        assertEquals("use either --category or --uncategorized, not both",
                stderr().strip());
        assertTrue(stdout().isEmpty());
    }

    @Test
    void listDoesNotModifyTheFile() throws IOException {
        String before = Files.readString(data(), StandardCharsets.UTF_8);

        cli().run("list");

        assertEquals(before, Files.readString(data(), StandardCharsets.UTF_8));
    }
}
