package io.github.lawrencekcleung.pft.cli;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * Thin facade over picocli: builds the command tree, configures streams and exit codes, and
 * returns the process exit code instead of exiting, so tests stay in-process.
 */
public final class Cli {

    /** The data file used when neither {@code --data} nor {@code PFT_DATA} is present. */
    public static final Path DEFAULT_DATA_PATH = Path.of("data/transactions.csv");

    private final CommandLine commandLine;

    private Cli(CommandLine commandLine) {
        this.commandLine = commandLine;
    }

    /**
     * Creates a CLI with the given dependencies and streams.
     *
     * @param clock           the clock passed to commands
     * @param env             environment variables, consulted for {@code PFT_DATA}
     * @param defaultDataPath the fallback data file
     * @param out             normal output
     * @param err             error output
     * @return the configured CLI
     */
    public static Cli create(Clock clock, Map<String, String> env, Path defaultDataPath,
                             PrintWriter out, PrintWriter err) {
        CommandLine commandLine = new CommandLine(
                new RootCommand(clock, env, defaultDataPath));
        configure(commandLine, Objects.requireNonNull(out, "out is required"),
                Objects.requireNonNull(err, "err is required"));
        return new Cli(commandLine);
    }

    private static void configure(CommandLine commandLine, PrintWriter out, PrintWriter err) {
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.setExecutionExceptionHandler(new ExecutionExceptionHandler());
        commandLine.setParameterExceptionHandler(new ParameterExceptionHandler());
        commandLine.getCommandSpec().exitCodeOnInvalidInput(1);
        for (CommandLine subcommand : commandLine.getSubcommands().values()) {
            configure(subcommand, out, err);
        }
    }

    /**
     * Runs one invocation.
     *
     * @param args the raw command line, without the program name
     * @return {@code 0} success, {@code 1} usage error, {@code 2} data or storage error
     */
    public int run(String... args) {
        return commandLine.execute(args);
    }
}
