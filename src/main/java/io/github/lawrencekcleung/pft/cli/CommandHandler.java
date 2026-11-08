package io.github.lawrencekcleung.pft.cli;

/**
 * Executes one command with its parsed arguments and application context.
 *
 * <p>Implementations report user errors by throwing {@code UsageException} (exit 1) or
 * {@code DataException} / {@code StorageException} (exit 2); {@link Cli} owns the mapping, so a
 * handler never calls {@link System#exit}.</p>
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * Runs the command.
     *
     * @param args the typed command-line arguments
     * @param ctx  the repository, streams and clock for this invocation
     */
    void run(ParsedArgs args, AppContext ctx);
}
