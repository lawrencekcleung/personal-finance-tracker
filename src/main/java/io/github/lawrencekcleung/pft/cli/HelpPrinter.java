package io.github.lawrencekcleung.pft.cli;

import java.util.List;

/**
 * Renders help from the command table and option specs.
 *
 * <p>Nothing here is hand-written per command: the root lists every registered command, and a
 * command page lists every option in {@link CommandDefinition#allOptions()}, including the
 * globals. Help therefore cannot drift from what the parser accepts.</p>
 */
public final class HelpPrinter {

    private static final String PROGRAM = "pft";

    /**
     * Renders the root help.
     *
     * @param registry the registered commands
     * @return the help text, without a trailing newline
     */
    public String root(CommandRegistry registry) {
        StringBuilder text = new StringBuilder();
        text.append(PROGRAM).append(" - personal finance tracker\n\n");
        text.append("Usage:\n");
        text.append("  ").append(PROGRAM).append(" [--data <path>] <command> [options]\n");
        text.append("  ").append(PROGRAM).append(" --help\n");
        text.append("  ").append(PROGRAM).append(" <command> --help\n");
        text.append("\nCommands:\n");

        if (registry.all().isEmpty()) {
            text.append("  (none registered yet)\n");
        } else {
            int width = registry.all().stream()
                    .mapToInt(definition -> definition.name().length())
                    .max()
                    .orElse(0) + 2;
            for (CommandDefinition definition : registry.all()) {
                text.append("  ").append(pad(definition.name(), width))
                        .append(definition.summary()).append('\n');
            }
        }

        text.append("\nOptions:\n");
        appendOptions(text, List.of(CommandDefinition.DATA, CommandDefinition.HELP));
        return text.toString().stripTrailing();
    }

    /**
     * Renders one command's help.
     *
     * @param definition the command
     * @return the help text, without a trailing newline
     */
    public String command(CommandDefinition definition) {
        StringBuilder text = new StringBuilder();
        text.append("Usage: ").append(PROGRAM).append(' ')
                .append(definition.name()).append(" [options]\n\n");
        text.append(definition.summary()).append("\n\n");
        text.append("Options:\n");
        appendOptions(text, definition.allOptions());
        return text.toString().stripTrailing();
    }

    private static void appendOptions(StringBuilder text, List<OptionSpec> options) {
        int width = options.stream()
                .mapToInt(option -> label(option).length())
                .max()
                .orElse(0) + 2;
        for (OptionSpec option : options) {
            text.append("  ").append(pad(label(option), width))
                    .append(describe(option)).append('\n');
        }
    }

    private static String label(OptionSpec option) {
        String label = "--" + option.name();
        String valueLabel = option.type().valueLabel();
        return valueLabel.isEmpty() ? label : label + " <" + valueLabel + ">";
    }

    private static String describe(OptionSpec option) {
        StringBuilder text = new StringBuilder(option.help());
        if (option.required()) {
            text.append(" (required)");
        }
        if (option.hasDefault()) {
            text.append(" (default: ").append(option.defaultValue()).append(')');
        }
        return text.toString();
    }

    private static String pad(String value, int width) {
        return String.format("%-" + width + "s", value);
    }
}
