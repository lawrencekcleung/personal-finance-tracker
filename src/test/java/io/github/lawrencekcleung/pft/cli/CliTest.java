package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    private Path defaultDataPath() {
        return dir.resolve("default.csv");
    }

    private Cli cli(List<CommandDefinition> commands) {
        return cli(commands, Map.of(), defaultDataPath());
    }

    private Cli cli(List<CommandDefinition> commands, Map<String, String> env, Path dataPath) {
        return new Cli(new CommandRegistry(commands), out, err, CLOCK, env, dataPath);
    }

    private static CommandDefinition command(String name, CommandHandler handler,
                                             OptionSpec... options) {
        return new CommandDefinition(name, "test command", List.of(options), handler);
    }

    private static Transaction draft(String description) {
        return new Transaction(0, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE,
                Money.ofCents(4250), "groceries", description);
    }

    private String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ help

    @Test
    void rootHelpSucceeds() {
        assertEquals(0, cli(List.of()).run("--help"));

        assertTrue(stdout().contains("pft - personal finance tracker"));
        assertTrue(stderr().isEmpty());
    }

    @Test
    void noArgumentsIsAUsageError() {
        assertEquals(1, cli(List.of()).run());

        assertTrue(stderr().contains("Usage:"));
    }

    @Test
    void unknownCommandIsAUsageError() {
        assertEquals(1, cli(List.of()).run("nope"));

        assertTrue(stderr().contains("unknown command 'nope'"));
    }

    @Test
    void commandHelpSucceedsAndDoesNotTouchTheDataFile() {
        CommandHandler handler = (args, ctx) -> {
            throw new AssertionError("help must not run the command");
        };

        assertEquals(0, cli(List.of(command("probe", handler))).run("probe", "--help"));

        assertTrue(stdout().contains("Usage: pft probe"));
        assertFalse(Files.exists(defaultDataPath()));
    }

    @Test
    void commandHelpWorksEvenWhenRequiredOptionsAreMissing() {
        OptionSpec date = new OptionSpec("date", "the day", OptionType.DATE, true, null);

        assertEquals(0, cli(List.of(command("probe", (args, ctx) -> {
            throw new AssertionError("help must not run the command");
        }, date))).run("probe", "--help"));

        assertTrue(stdout().contains("Usage: pft probe"));
        assertTrue(stdout().contains("--date <yyyy-MM-dd>"));
        assertTrue(stderr().isEmpty());
    }

    // ------------------------------------------------------------ exit codes

    @Test
    void usageExceptionMapsToOne() {
        CommandHandler handler = (args, ctx) -> {
            throw new UsageException("bad flag");
        };

        assertEquals(1, cli(List.of(command("probe", handler))).run("probe"));
        assertEquals("bad flag", stderr().strip());
    }

    @Test
    void dataExceptionMapsToTwo() {
        CommandHandler handler = (args, ctx) -> {
            throw new DataException("unknown transaction id: 7");
        };

        assertEquals(2, cli(List.of(command("probe", handler))).run("probe"));
        assertEquals("unknown transaction id: 7", stderr().strip());
    }

    @Test
    void storageExceptionMapsToTwo() {
        CommandHandler handler = (args, ctx) -> {
            throw new StorageException("cannot read store");
        };

        assertEquals(2, cli(List.of(command("probe", handler))).run("probe"));
        assertEquals("cannot read store", stderr().strip());
    }

    // --------------------------------------------------------------- data path

    @Test
    void aSuccessfulCommandRunsAgainstTheResolvedRepository() throws Exception {
        CommandHandler handler = (args, ctx) -> ctx.repository().insert(draft("Shop"));

        assertEquals(0, cli(List.of(command("probe", handler))).run("probe"));

        assertTrue(Files.exists(defaultDataPath()), "the command should have written the store");
        assertTrue(Files.readString(defaultDataPath(), StandardCharsets.UTF_8)
                .contains("Shop"));
    }

    @Test
    void dataFlagBeforeTheCommandIsHonoured() {
        Path dataPath = dir.resolve("before.csv");
        AppContext[] seen = new AppContext[1];
        CommandHandler handler = (args, ctx) -> seen[0] = ctx;

        assertEquals(0, cli(List.of(command("probe", handler))).run(
                "--data", dataPath.toString(), "probe"));

        assertEquals(dataPath, seen[0].dataPath());
    }

    @Test
    void dataFlagAfterTheCommandIsHonoured() {
        Path dataPath = dir.resolve("after.csv");
        AppContext[] seen = new AppContext[1];
        CommandHandler handler = (args, ctx) -> seen[0] = ctx;

        assertEquals(0, cli(List.of(command("probe", handler))).run(
                "probe", "--data=" + dataPath));

        assertEquals(dataPath, seen[0].dataPath());
    }

    @Test
    void environmentVariableIsUsedWhenTheFlagIsAbsent() {
        Path dataPath = dir.resolve("env.csv");
        AppContext[] seen = new AppContext[1];
        CommandHandler handler = (args, ctx) -> seen[0] = ctx;

        assertEquals(0, cli(List.of(command("probe", handler)),
                Map.of("PFT_DATA", dataPath.toString()), defaultDataPath()).run("probe"));

        assertEquals(dataPath, seen[0].dataPath());
    }

    @Test
    void theFlagWinsOverTheEnvironment() {
        Path fromFlag = dir.resolve("flag.csv");
        AppContext[] seen = new AppContext[1];
        CommandHandler handler = (args, ctx) -> seen[0] = ctx;

        assertEquals(0, cli(List.of(command("probe", handler)),
                Map.of("PFT_DATA", dir.resolve("env.csv").toString()), defaultDataPath())
                .run("--data", fromFlag.toString(), "probe"));

        assertEquals(fromFlag, seen[0].dataPath());
    }

    @Test
    void theDefaultPathIsUsedWhenNothingOverridesIt() {
        AppContext[] seen = new AppContext[1];
        CommandHandler handler = (args, ctx) -> seen[0] = ctx;

        assertEquals(0, cli(List.of(command("probe", handler))).run("probe"));

        assertEquals(defaultDataPath(), seen[0].dataPath());
    }

    // ------------------------------------------------------------ bad input

    @Test
    void unknownOptionIsAUsageErrorListingTheAlternatives() {
        assertEquals(1, cli(List.of(command("probe", (args, ctx) -> { }))).run(
                "probe", "--nope"));

        assertTrue(stderr().contains("unknown option --nope"));
        assertTrue(stderr().contains("valid options:"));
    }

    @Test
    void missingRequiredOptionIsAUsageError() {
        OptionSpec date = new OptionSpec("date", "the day", OptionType.DATE, true, null);

        assertEquals(1, cli(List.of(command("probe", (args, ctx) -> { }, date))).run("probe"));

        assertTrue(stderr().contains("missing required option --date"));
    }

    @Test
    void unsupportedDataFormatIsAUsageError() {
        assertEquals(1, cli(List.of(command("probe", (args, ctx) -> { }))).run(
                "--data", "pft.db", "probe"));

        assertTrue(stderr().contains("unsupported data file 'pft.db'"));
    }

    @Test
    void missingDataValueBeforeTheCommandIsAUsageError() {
        assertEquals(1, cli(List.of()).run("--data"));

        assertTrue(stderr().contains("--data: missing value"));
    }

    @Test
    void positionalArgumentAfterTheCommandIsAUsageError() {
        assertEquals(1, cli(List.of(command("probe", (args, ctx) -> { }))).run(
                "probe", "extra"));

        assertTrue(stderr().contains("unexpected argument 'extra'"));
    }
}
