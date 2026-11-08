package io.github.lawrencekcleung.pft.cli;

import java.util.Objects;

/**
 * Declares one command-line option: its name, help text, value kind, and whether it is required
 * or has a default.
 *
 * <p>Options are declared once, here, and the parser, help printer and tests all derive from that
 * declaration, so help text and accepted flags cannot drift apart.</p>
 *
 * @param name         the option name without leading dashes, for example {@code date}
 * @param help         one-line help text shown in generated help
 * @param type         the value kind and converter
 * @param required     whether the user must supply the option
 * @param defaultValue the value used when the option is absent, or {@code null} for none
 */
public record OptionSpec(String name, String help, OptionType type,
                         boolean required, String defaultValue) {

    /**
     * Validates the declaration.
     *
     * @throws IllegalArgumentException if the name is blank or dashed, or if the required/default
     *                                  or flag/default combinations conflict
     */
    public OptionSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("option name is required");
        }
        if (name.startsWith("-")) {
            throw new IllegalArgumentException(
                    "option name must not start with '-': " + name);
        }
        Objects.requireNonNull(help, "help is required");
        Objects.requireNonNull(type, "type is required");
        if (type == OptionType.FLAG && defaultValue != null) {
            throw new IllegalArgumentException(
                    "flag --" + name + " takes no value, so it cannot have a default");
        }
        if (required && defaultValue != null) {
            throw new IllegalArgumentException(
                    "required option --" + name + " cannot have a default");
        }
    }

    /**
     * Returns whether an absent option has a declared default.
     *
     * @return {@code true} when {@link #defaultValue()} is not {@code null}
     */
    public boolean hasDefault() {
        return defaultValue != null;
    }
}
