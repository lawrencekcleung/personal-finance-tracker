package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.cli.commands.AddCommand;
import io.github.lawrencekcleung.pft.cli.commands.AnalyzeCommand;
import io.github.lawrencekcleung.pft.cli.commands.CategorizeCommand;
import io.github.lawrencekcleung.pft.cli.commands.DeleteCommand;
import io.github.lawrencekcleung.pft.cli.commands.ImportCommand;
import io.github.lawrencekcleung.pft.cli.commands.ListCommand;
import io.github.lawrencekcleung.pft.cli.commands.SummaryCommand;
import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.store.RepositoryFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * The root command: global options, the command table and the shared application context.
 *
 * <p>Picocli owns parsing, help and subcommand dispatch; this class only declares the globals and
 * knows how to resolve the data file and open the repository for a command.</p>
 */
@Command(
        name = "pft",
        description = "A small command-line tracker for personal income and expenses.",
        mixinStandardHelpOptions = true,
        version = "pft 0.1.0",
        subcommands = {
                CommandLine.HelpCommand.class,
                AddCommand.class,
                ImportCommand.class,
                ListCommand.class,
                CategorizeCommand.class,
                SummaryCommand.class,
                AnalyzeCommand.class,
                DeleteCommand.class
        },
        footer = {
                "",
                "Examples:",
                "  pft add --date 2026-11-01 --type expense --amount 42.50 "
                        + "--description Groceries",
                "  pft list --month 2026-11",
                "  pft summary --month 2026-11",
                "",
                "Run 'pft help <command>' or 'pft <command> --help' for details."
        })
public final class RootCommand implements java.util.concurrent.Callable<Integer> {

    @Option(names = "--data", scope = ScopeType.INHERIT, paramLabel = "<path>",
            description = "data file (default: ./data/transactions.csv, or $PFT_DATA)")
    private Path dataOption;

    @Spec
    private CommandSpec spec;

    private final Clock clock;
    private final Map<String, String> env;
    private final Path defaultDataPath;

    RootCommand(Clock clock, Map<String, String> env, Path defaultDataPath) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.env = Map.copyOf(Objects.requireNonNull(env, "env is required"));
        this.defaultDataPath =
                Objects.requireNonNull(defaultDataPath, "defaultDataPath is required");
    }

    /**
     * Runs when no subcommand is given: shows usage and reports a usage error.
     *
     * @return exit code {@code 1}
     */
    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getErr());
        return 1;
    }

    /**
     * Opens the resolved data file and builds the context for one command invocation.
     *
     * @return the open context, to be closed by the command
     */
    public AppContext context() {
        CommandLine commandLine = spec.commandLine();
        Path path = resolveDataPath();
        return new AppContext(RepositoryFactory.open(path), commandLine.getOut(),
                commandLine.getErr(), clock, path);
    }

    private Path resolveDataPath() {
        if (dataOption != null) {
            return dataOption;
        }
        String fromEnv = env.get("PFT_DATA");
        if (fromEnv != null && !fromEnv.isBlank()) {
            try {
                return Path.of(fromEnv);
            } catch (InvalidPathException e) {
                throw new UsageException("PFT_DATA: invalid path '" + fromEnv + "'");
            }
        }
        return defaultDataPath;
    }
}
