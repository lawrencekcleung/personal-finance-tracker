package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.store.CsvCodec;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvCorruptionTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    private Path data() {
        return dir.resolve("data/transactions.csv");
    }

    private void assertCorrupt(String content, String expectedMessage) throws IOException {
        Files.createDirectories(data().getParent());
        Files.writeString(data(), content, StandardCharsets.UTF_8);
        String before = Files.readString(data(), StandardCharsets.UTF_8);

        int exit = new Cli(CommandRegistry.defaults(), out, err, CLOCK, Map.of(), data())
                .run("list");

        assertEquals(2, exit);
        assertEquals(expectedMessage + "\n", errBytes.toString(StandardCharsets.UTF_8));
        assertTrue(outBytes.toString(StandardCharsets.UTF_8).isEmpty());
        assertEquals(before, Files.readString(data(), StandardCharsets.UTF_8),
                "a corrupt store must not be rewritten");
    }

    @Test
    void wrongColumnCountAborts() throws IOException {
        assertCorrupt(CsvCodec.HEADER + "\n1,2026-11-01,expense,42.50,groceries\n",
                data() + ":2: expected 6 columns, got 5");
    }

    @Test
    void invalidAmountAborts() throws IOException {
        assertCorrupt(CsvCodec.HEADER + "\n1,2026-11-01,expense,abc,groceries,Shop\n",
                data() + ":2: invalid amount 'abc': expected a non-negative number "
                        + "with at most 2 decimal places");
    }

    @Test
    void duplicateIdAborts() throws IOException {
        assertCorrupt(CsvCodec.HEADER + "\n"
                        + "1,2026-11-01,expense,42.50,groceries,One\n"
                        + "1,2026-11-02,expense,10.00,dining,Two\n",
                data() + ":3: duplicate id 1");
    }

    @Test
    void invalidDateAborts() throws IOException {
        assertCorrupt(CsvCodec.HEADER + "\n1,2026-13-01,expense,42.50,groceries,Shop\n",
                data() + ":2: invalid date '2026-13-01': expected yyyy-MM-dd");
    }
}
