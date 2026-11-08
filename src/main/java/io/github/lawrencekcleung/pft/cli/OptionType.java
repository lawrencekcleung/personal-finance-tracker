package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Money;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * The value kind of a declared option, including how to convert its text to a typed value.
 *
 * <p>Conversion happens eagerly in {@link ArgumentParser}; a failure becomes a
 * {@link UsageException} with one consistent shape, {@code --name: expected <kind>}, so a bad
 * {@code --date} and a bad {@code --amount} read the same way.</p>
 */
public enum OptionType {

    /** Free text. */
    STRING("value", "a value", value -> value),

    /** A switch that takes no value. */
    FLAG("", "no value", value -> Boolean.TRUE),

    /** An ISO local date, {@code yyyy-MM-dd}. */
    DATE("yyyy-MM-dd", "yyyy-MM-dd",
            value -> LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)),

    /** A non-negative decimal amount with at most two decimal places. */
    AMOUNT("amount", "an amount with at most 2 decimals", Money::parse),

    /** A calendar month, {@code yyyy-MM}. */
    YEAR_MONTH("yyyy-MM", "yyyy-MM", YearMonth::parse),

    /** A whole number. */
    INT("integer", "an integer", Integer::parseInt),

    /** A file-system path. */
    PATH("path", "a path", Path::of);

    private final String valueLabel;
    private final String expectation;
    private final Function<String, Object> converter;

    OptionType(String valueLabel, String expectation, Function<String, Object> converter) {
        this.valueLabel = valueLabel;
        this.expectation = expectation;
        this.converter = converter;
    }

    /**
     * The placeholder shown after the option name in help, for example {@code yyyy-MM-dd}. Empty
     * for {@link #FLAG}.
     *
     * @return the help placeholder, never {@code null}
     */
    public String valueLabel() {
        return valueLabel;
    }

    /**
     * Converts raw option text to this type.
     *
     * @param optionName the option name, used in the error message
     * @param raw        the text supplied by the user
     * @return the converted value
     * @throws UsageException if the text does not convert
     */
    Object convert(String optionName, String raw) {
        try {
            return converter.apply(raw);
        } catch (RuntimeException e) {
            throw new UsageException("--" + optionName + ": expected " + expectation);
        }
    }
}
