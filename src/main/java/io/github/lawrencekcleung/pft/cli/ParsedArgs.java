package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.model.Money;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The typed result of parsing one command line.
 *
 * <p>Values were converted eagerly by {@link ArgumentParser}, so accessors only type-check and
 * unwrap. The command definitions are the source of truth for each option's kind: asking for an
 * {@code --amount} as a string is a programming error, not a user error, and raises
 * {@link IllegalStateException}. Accessing a name that was never declared raises
 * {@link IllegalArgumentException}.</p>
 */
public final class ParsedArgs {

    private final Map<String, Object> values;
    private final Map<String, OptionType> types;

    ParsedArgs(Map<String, Object> values, Map<String, OptionType> types) {
        this.values = Map.copyOf(values);
        this.types = Map.copyOf(types);
    }

    /**
     * Returns a required string option.
     *
     * @param name the option name
     * @return the value
     * @throws IllegalStateException if the option is absent or is not a {@code STRING}
     */
    public String requireString(String name) {
        Object value = checked(name, OptionType.STRING);
        if (value == null) {
            throw new IllegalStateException("--" + name + " is required but was not provided");
        }
        return (String) value;
    }

    /**
     * Returns an optional string option.
     *
     * @param name the option name
     * @return the value, or empty when the option is absent
     * @throws IllegalStateException if the option is not a {@code STRING}
     */
    public Optional<String> optionalString(String name) {
        return Optional.ofNullable((String) checked(name, OptionType.STRING));
    }

    /**
     * Returns a required amount option.
     *
     * @param name the option name
     * @return the value
     * @throws IllegalStateException if the option is absent or is not an {@code AMOUNT}
     */
    public Money requireAmount(String name) {
        return (Money) required(name, OptionType.AMOUNT);
    }

    /**
     * Returns a required date option.
     *
     * @param name the option name
     * @return the value
     * @throws IllegalStateException if the option is absent or is not a {@code DATE}
     */
    public LocalDate requireDate(String name) {
        return (LocalDate) required(name, OptionType.DATE);
    }

    /**
     * Returns an optional month option.
     *
     * @param name the option name
     * @return the value, or empty when the option is absent
     * @throws IllegalStateException if the option is not a {@code YEAR_MONTH}
     */
    public Optional<YearMonth> optionalMonth(String name) {
        return Optional.ofNullable((YearMonth) checked(name, OptionType.YEAR_MONTH));
    }

    /**
     * Returns a required integer option.
     *
     * @param name the option name
     * @return the value
     * @throws IllegalStateException if the option is absent or is not an {@code INT}
     */
    public int requireInt(String name) {
        return (Integer) required(name, OptionType.INT);
    }

    /**
     * Returns an optional integer option.
     *
     * @param name the option name
     * @return the value, or empty when the option is absent
     * @throws IllegalStateException if the option is not an {@code INT}
     */
    public OptionalInt optionalInt(String name) {
        Integer value = (Integer) checked(name, OptionType.INT);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    /**
     * Returns an optional path option.
     *
     * @param name the option name
     * @return the value, or empty when the option is absent
     * @throws IllegalStateException if the option is not a {@code PATH}
     */
    public Optional<Path> optionalPath(String name) {
        return Optional.ofNullable((Path) checked(name, OptionType.PATH));
    }

    /**
     * Returns whether a flag was supplied.
     *
     * @param name the option name
     * @return {@code true} when the flag is present, {@code false} when it is absent
     * @throws IllegalStateException if the option is not a {@code FLAG}
     */
    public boolean flag(String name) {
        return Boolean.TRUE.equals(checked(name, OptionType.FLAG));
    }

    private Object required(String name, OptionType expected) {
        Object value = checked(name, expected);
        if (value == null) {
            throw new IllegalStateException("--" + name + " is required but was not provided");
        }
        return value;
    }

    private Object checked(String name, OptionType expected) {
        OptionType actual = types.get(name);
        if (actual == null) {
            throw new IllegalArgumentException("unknown option --" + name);
        }
        if (actual != expected) {
            throw new IllegalStateException(
                    "--" + name + " is a " + actual + " option, not " + expected);
        }
        return values.get(name);
    }
}
