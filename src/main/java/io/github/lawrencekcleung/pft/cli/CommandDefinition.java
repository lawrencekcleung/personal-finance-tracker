package io.github.lawrencekcleung.pft.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Declares one command: its name, summary, options and handler.
 *
 * <p>{@link #allOptions()} appends the two globals ({@code --data}, {@code --help}) in one place,
 * so no command has to remember them and help stays consistent. Commands may not redeclare a
 * global.</p>
 *
 * @param name    the command word, for example {@code add}
 * @param summary one-line description shown in root help
 * @param options the command-specific options, without the globals
 * @param handler the code that runs the command
 */
public record CommandDefinition(String name, String summary, List<OptionSpec> options,
                                CommandHandler handler) {

    /** The global data-file option available to every command. */
    public static final OptionSpec DATA = new OptionSpec("data",
            "data file (default: ./data/transactions.csv, or $PFT_DATA)",
            OptionType.PATH, false, null);

    /** The global help flag available to every command. */
    public static final OptionSpec HELP = new OptionSpec("help",
            "show help for this command", OptionType.FLAG, false, null);

    /**
     * Validates the definition and freezes the option list.
     *
     * @throws IllegalArgumentException if the name is blank or dashed, or a command option
     *                                  redeclares a global
     */
    public CommandDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("command name is required");
        }
        if (name.startsWith("-")) {
            throw new IllegalArgumentException("command name must not start with '-': " + name);
        }
        Objects.requireNonNull(summary, "summary is required");
        options = List.copyOf(Objects.requireNonNull(options, "options is required"));
        Objects.requireNonNull(handler, "handler is required");
        for (OptionSpec option : options) {
            if (option.name().equals(DATA.name()) || option.name().equals(HELP.name())) {
                throw new IllegalArgumentException(
                        "option --" + option.name() + " is reserved for the CLI");
            }
        }
    }

    /**
     * Returns the command-specific options followed by the globals.
     *
     * @return an immutable list of every option this command accepts
     */
    public List<OptionSpec> allOptions() {
        List<OptionSpec> all = new ArrayList<>(options.size() + 2);
        all.addAll(options);
        all.add(DATA);
        all.add(HELP);
        return List.copyOf(all);
    }
}
