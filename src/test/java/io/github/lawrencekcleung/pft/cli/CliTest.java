package io.github.lawrencekcleung.pft.cli;

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

class CliTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintWriter out = new PrintWriter(outBytes, true, StandardCharsets.UTF_8);
    private final PrintWriter err = new PrintWriter(errBytes, true, StandardCharsets.UTF_8);

    private Path defaultDataPath() {
        return dir.resolve("default.csv");
    }

    private Cli cli() {
        return Cli.create(CLOCK, Map.of(), defaultDataPath(), out, err);
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

    private static String[] addArgs(String dataPath) {
        return new String[] {"add", "--date", "2026-11-01", "--type", "income",
                "--amount", "1.00", "--description", "One", "--data", dataPath};
    }

    // ------------------------------------------------------------------ help

    @Test
    void helpListsEveryCommandAndSucceeds() {
        assertEquals(0, run("--help"));

        assertTrue(stdout().contains("Usage:"));
        for (String command : new String[] {"add", "import", "list", "categorize",
                "summary", "analyze", "delete"}) {
            assertTrue(stdout().contains(command), () -> "help is missing " + command);
        }
        assertTrue(stderr().isEmpty());
    }

    @Test
    void shortHelpAlsoWorks() {
        assertEquals(0, run("-h"));
        assertTrue(stdout().contains("Usage:"));
    }

    @Test
    void versionSucceeds() {
        assertEquals(0, run("--version"));
        assertTrue(stdout().contains("pft 0.1.0"));
    }

    @Test
    void helpSubcommandShowsCommandHelp() {
        assertEquals(0, run("help", "add"));

        assertTrue(stdout().contains("--description"));
        assertTrue(stdout().contains("add"));
    }

    @Test
    void commandHelpSucceedsWithoutRequiredOptions() throws Exception {
        assertEquals(0, run("add", "--help"));

        assertTrue(stdout().contains("--date"));
        assertFalse(Files.exists(defaultDataPath()));
    }

    @Test
    void noArgumentsIsAUsageError() {
        assertEquals(1, run());
        assertTrue(stderr().contains("Usage:"));
    }

    // ------------------------------------------------------------ bad input

    @Test
    void unknownCommandIsAUsageError() {
        assertEquals(1, run("nope"));

        assertTrue(stderr().contains("nope"));
        assertTrue(stdout().isEmpty());
    }

    @Test
    void aMisspelledCommandSuggestsTheClosestOne() {
        assertEquals(1, run("lst"));

        assertTrue(stderr().contains("Did you mean"));
        assertTrue(stderr().contains("list"));
    }

    @Test
    void unknownOptionIsAUsageError() {
        assertEquals(1, run("add", "--date", "2026-11-01", "--type", "income",
                "--amount", "1.00", "--description", "One", "--nope"));

        assertTrue(stderr().contains("Unknown option"));
        assertTrue(stderr().contains("--nope"));
        assertTrue(stdout().isEmpty());
    }

    @Test
    void aMisspelledOptionSuggestsTheClosestOne() {
        assertEquals(1, run("add", "--descriptin", "X"));

        assertTrue(stderr().contains("--description"));
    }

    @Test
    void missingRequiredOptionIsAUsageError() {
        assertEquals(1, run("add"));

        assertTrue(stderr().contains("--date"));
    }

    @Test
    void dataErrorsExitTwo() {
        assertEquals(2, run("add",
                "--date", "2026-11-01", "--type", "transfer",
                "--amount", "1.00", "--description", "Move"));

        assertTrue(stderr().contains("unknown transaction type: transfer"));
        assertFalse(Files.exists(defaultDataPath()));
    }

    // -------------------------------------------------------------- data path

    @Test
    void dataFlagBeforeTheCommandIsHonoured() throws Exception {
        Path path = dir.resolve("before.csv");

        assertEquals(0, run("--data", path.toString(), "add",
                "--date", "2026-11-01", "--type", "income", "--amount", "1.00",
                "--description", "One"));

        assertTrue(Files.exists(path));
    }

    @Test
    void dataFlagAfterTheCommandIsHonoured() throws Exception {
        Path path = dir.resolve("after.csv");

        assertEquals(0, run("add", "--date", "2026-11-01", "--type", "income",
                "--amount", "1.00", "--description", "One", "--data", path.toString()));

        assertTrue(Files.exists(path));
    }

    @Test
    void environmentVariableIsUsedWhenTheFlagIsAbsent() throws Exception {
        Path path = dir.resolve("env.csv");
        Cli cli = Cli.create(CLOCK, Map.of("PFT_DATA", path.toString()),
                defaultDataPath(), out, err);

        assertEquals(0, cli.run("add", "--date", "2026-11-01", "--type", "income",
                "--amount", "1.00", "--description", "One"));

        assertTrue(Files.exists(path));
    }

    @Test
    void theFlagWinsOverTheEnvironment() throws Exception {
        Path fromFlag = dir.resolve("flag.csv");
        Cli cli = Cli.create(CLOCK,
                Map.of("PFT_DATA", dir.resolve("env.csv").toString()),
                defaultDataPath(), out, err);

        assertEquals(0, cli.run(addArgs(fromFlag.toString())));

        assertTrue(Files.exists(fromFlag));
        assertFalse(Files.exists(dir.resolve("env.csv")));
    }

    @Test
    void theDefaultPathIsUsedWhenNothingOverridesIt() throws Exception {
        assertEquals(0, run("add", "--date", "2026-11-01", "--type", "income",
                "--amount", "1.00", "--description", "One"));

        assertTrue(Files.exists(defaultDataPath()));
    }
}
