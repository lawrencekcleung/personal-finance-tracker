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
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeleteCommandTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

    private static final String FIXTURE = CsvCodec.HEADER + "\n"
            + "1,2026-11-01,income,5000.00,income,Salary Oct\n"
            + "2,2026-11-02,expense,42.50,groceries,Countdown\n";

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

    @BeforeEach
    void writeFixture() throws IOException {
        Files.createDirectories(data().getParent());
        Files.writeString(data(), FIXTURE, StandardCharsets.UTF_8);
    }

    @Test
    void deleteRemovesTheRowAndPrintsTheId() throws IOException {
        assertEquals(0, cli().run("delete", "--id", "2"));

        assertEquals("Deleted #2.\n", stdout());
        assertEquals(CsvCodec.HEADER + "\n"
                        + "1,2026-11-01,income,5000.00,income,Salary Oct\n",
                Files.readString(data(), StandardCharsets.UTF_8));
    }

    @Test
    void unknownIdIsADataErrorAndLeavesTheFileUntouched() throws IOException {
        String before = Files.readString(data(), StandardCharsets.UTF_8);

        assertEquals(2, cli().run("delete", "--id", "99"));

        assertEquals("unknown transaction id: 99\n", stderr());
        assertTrue(stdout().isEmpty());
        assertEquals(before, Files.readString(data(), StandardCharsets.UTF_8));
    }

    @Test
    void missingIdIsAUsageErrorAndWritesNothing() {
        assertEquals(1, cli().run("delete"));

        assertTrue(stderr().contains("Missing required option"));
        assertTrue(stderr().contains("--id"));
        assertTrue(stdout().isEmpty());
    }

    @Test
    void aDeletedIdIsNotReusedByALaterAdd() throws IOException {
        Files.delete(data());
        Cli cli = cli();
        cli.run("add", "--date", "2026-11-01", "--type", "income", "--amount", "1.00",
                "--description", "One");
        outBytes.reset();

        cli.run("delete", "--id", "1");
        outBytes.reset();
        cli.run("add", "--date", "2026-11-02", "--type", "expense", "--amount", "2.00",
                "--description", "Two");

        assertEquals("Added #2 (category: UNCATEGORIZED)\n", stdout());
    }
}
