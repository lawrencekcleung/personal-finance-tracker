package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.store.RepositoryFactory;
import io.github.lawrencekcleung.pft.store.TransactionRepository;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The command dispatcher: resolves the data file, parses arguments, runs the matched handler and
 * maps failures to exit codes.
 *
 * <p>Exit codes follow the PRD: {@code 0} success, {@code 1} usage error, {@code 2} data or
 * storage error. {@link #run(String...)} returns the code instead of exiting, which keeps every
 * CLI test in-process.</p>
 */
public final class Cli {

    /** The data file used when neither {@code --data} nor {@code PFT_DATA} is present. */
    public static final Path DEFAULT_DATA_PATH = Path.of("data/transactions.csv");

    private static final String DATA_ENV = "PFT_DATA";

    private final CommandRegistry registry;
    private final HelpPrinter help;
    private final ArgumentParser parser;
    private final PrintStream out;
    private final PrintStream err;
    private final Clock clock;
    private final Map<String, String> env;
    private final Path defaultDataPath;

    /**
     * Creates a CLI facade.
     *
     * @param registry        the commands to dispatch to
     * @param out             standard output
     * @param err             standard error
     * @param clock           the clock passed to commands
     * @param env             environment variables, consulted for {@code PFT_DATA}
     * @param defaultDataPath the fallback data file
     */
    public Cli(CommandRegistry registry, PrintStream out, PrintStream err, Clock clock,
               Map<String, String> env, Path defaultDataPath) {
        this.registry = Objects.requireNonNull(registry, "registry is required");
        this.help = new HelpPrinter();
        this.parser = new ArgumentParser();
        this.out = Objects.requireNonNull(out, "out is required");
        this.err = Objects.requireNonNull(err, "err is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.env = Map.copyOf(Objects.requireNonNull(env, "env is required"));
        this.defaultDataPath = Objects.requireNonNull(defaultDataPath, "defaultDataPath is required");
    }

    /**
     * Builds the production CLI around a registry.
     *
     * @param registry the commands to dispatch to
     * @return a CLI wired to the real streams, environment and system clock
     */
    public static Cli defaults(CommandRegistry registry) {
        return new Cli(registry, System.out, System.err, Clock.systemDefaultZone(),
                System.getenv(), DEFAULT_DATA_PATH);
    }

    /**
     * Runs one invocation.
     *
     * @param args the raw command line, without the program name
     * @return the exit code: {@code 0} success, {@code 1} usage error, {@code 2} data/storage error
     */
    public int run(String... args) {
        try {
            return dispatch(List.of(args));
        } catch (UsageException e) {
            err.println(e.getMessage());
            return 1;
        } catch (DataException | StorageException e) {
            err.println(e.getMessage());
            return 2;
        }
    }

    private int dispatch(List<String> tokens) {
        int commandIndex = findCommandIndex(tokens);
        if (commandIndex < 0) {
            boolean helpRequested = tokens.contains("--help");
            (helpRequested ? out : err).println(help.root(registry));
            return helpRequested ? 0 : 1;
        }

        String name = tokens.get(commandIndex);
        CommandDefinition definition = registry.find(name).orElseThrow(
                () -> new UsageException("unknown command '" + name + "'; try 'pft --help'"));

        List<String> optionTokens = new ArrayList<>(tokens);
        optionTokens.remove(commandIndex);
        ParsedArgs parsed = parser.parse(definition.allOptions(), optionTokens);
        if (parsed.flag("help")) {
            out.println(help.command(definition));
            return 0;
        }

        Path dataPath = resolveDataPath(parsed);
        try (TransactionRepository repository = RepositoryFactory.open(dataPath)) {
            definition.handler().run(parsed,
                    new AppContext(repository, out, err, clock, dataPath));
        }
        return 0;
    }

    /**
     * Finds the command token, skipping the globals that may precede it. Returns {@code -1} when
     * the line contains no command (root help, or a usage error).
     */
    private static int findCommandIndex(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("--help")) {
                continue;
            }
            if (token.startsWith("--data=")) {
                continue;
            }
            if (token.equals("--data")) {
                if (i + 1 >= tokens.size() || tokens.get(i + 1).startsWith("--")) {
                    throw new UsageException("--data: missing value");
                }
                i++;
                continue;
            }
            if (token.startsWith("--")) {
                String option = token.contains("=")
                        ? token.substring(0, token.indexOf('='))
                        : token;
                throw new UsageException("unknown option " + option);
            }
            return i;
        }
        return -1;
    }

    private Path resolveDataPath(ParsedArgs args) {
        Optional<Path> fromFlag = args.optionalPath("data");
        if (fromFlag.isPresent()) {
            return fromFlag.get();
        }
        String fromEnv = env.get(DATA_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            try {
                return Path.of(fromEnv);
            } catch (InvalidPathException e) {
                throw new UsageException(DATA_ENV + ": invalid path '" + fromEnv + "'");
            }
        }
        return defaultDataPath;
    }
}
