package io.github.lawrencekcleung.pft.cli;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportCommandTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

    private static final String SEED = CsvCodec.HEADER + "\n"
            + "1,2026-11-01,income,5000.00,income,Salary Oct\n"
            + "2,2026-11-01,expense,42.50,groceries,Countdown\n"
            + "3,2026-11-02,expense,1200.00,rent,November rent\n";

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

    private String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private List<Transaction> stored() {
        return new CsvTransactionRepository(data()).findAll();
    }

    private void writeImport(String content) throws IOException {
        Files.writeString(importFile(), content, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void seedStore() throws IOException {
        Files.createDirectories(data().getParent());
        Files.writeString(data(), SEED, StandardCharsets.UTF_8);
    }

    @Test
    void partialImportCommitsValidRowsAndExitsTwo() throws IOException {
        writeImport("""
                date,type,amount,description,category
                2026-11-04,expense,15.80,Uber to airport,transport
                2026-11-05,expense,68.40,Countdown weekly shop,groceries
                2026-11-06,expense,12.00,Spotify,subscriptions
                2026-11-06,expense,26.10,Lunch Ponsonby,client-entertainment
                2026-11-07,expense,abc,Broken,dining
                """);

        int exit = cli().run("import", "--file", importFile().toString());

        assertEquals(2, exit);
        assertEquals("Imported 4 transactions.\n", stdout());
        assertTrue(stderr().contains(importFile() + ":6: invalid amount 'abc'"));
        assertTrue(stderr().contains("Skipped 1 row."));

        List<Transaction> transactions = stored();
        assertEquals(7, transactions.size());
        assertEquals(List.of(4, 5, 6, 7),
                transactions.stream().map(Transaction::id).toList().subList(3, 7));
        assertEquals("Lunch Ponsonby", transactions.get(6).description());
    }

    @Test
    void cleanImportExitsZero() throws IOException {
        writeImport("""
                2026-11-04,expense,15.80,Uber to airport,transport
                2026-11-05,expense,68.40,Countdown weekly shop,groceries
                """);

        int exit = cli().run("import", "--file=" + importFile());

        assertEquals(0, exit);
        assertEquals("Imported 2 transactions.\n", stdout());
        assertTrue(stderr().isEmpty());
        assertEquals(5, stored().size());
    }

    @Test
    void headerlessImportWorks() throws IOException {
        writeImport("2026-11-04,income,10.00,Refund\n");

        assertEquals(0, cli().run("import", "--file", importFile().toString()));
        assertEquals("Imported 1 transaction.\n", stdout());
    }

    @Test
    void allRowsBadImportsNothingAndExitsTwo() throws IOException {
        writeImport("""
                2026-13-01,expense,1.00,Bad date
                2026-11-01,transfer,1.00,Bad type
                """);

        assertEquals(2, cli().run("import", "--file", importFile().toString()));

        assertEquals("Imported 0 transactions.\n", stdout());
        assertTrue(stderr().contains("Skipped 2 rows."));
        assertEquals(3, stored().size(), "the seeded rows must be untouched");
    }

    @Test
    void missingFileOptionIsAUsageError() {
        assertEquals(1, cli().run("import"));

        assertTrue(stderr().contains("Missing required option"));
        assertTrue(stderr().contains("--file"));
        assertTrue(Files.exists(data()), "the store must still be there");
    }

    @Test
    void unreadableFileIsAStorageError() {
        Path missing = dir.resolve("missing.csv");

        assertEquals(2, cli().run("import", "--file", missing.toString()));

        assertTrue(stderr().contains("cannot read " + missing));
        assertTrue(stdout().isEmpty());
    }
}
