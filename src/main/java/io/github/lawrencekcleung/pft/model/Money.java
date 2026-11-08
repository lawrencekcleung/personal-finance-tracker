package io.github.lawrencekcleung.pft.model;

import java.util.regex.Pattern;

/**
 * An immutable monetary amount held as a whole number of cents.
 *
 * <p>Amounts are stored as {@code long} integer cents so that arithmetic is exact and free of
 * binary floating-point rounding. By convention individual transaction amounts are
 * non-negative magnitudes (income and expense are distinguished by the surrounding record, not
 * by the sign), while aggregates such as net totals may legitimately be negative; that is why
 * {@link #minus(Money)} and {@link #negate()} are allowed to produce negative values.</p>
 *
 * <p>Arithmetic is overflow-checked: operations that would leave the {@code long} range throw
 * {@link ArithmeticException} instead of silently wrapping.</p>
 */
public record Money(long cents) implements Comparable<Money> {

    /** The zero amount. */
    public static final Money ZERO = new Money(0);

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d+(\\.\\d{1,2})?$");

    /**
     * Creates an amount from a whole number of cents.
     *
     * @param cents the amount in cents
     * @return a {@code Money} for that amount
     */
    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    /**
     * Parses a non-negative decimal amount, such as user input, into a {@code Money}.
     *
     * <p>Accepted forms are plain decimal numbers with an optional fractional part of one or two
     * digits: {@code 42}, {@code 42.5} and {@code 42.50}. Anything else is rejected, including
     * negative amounts, more than two decimal places, thousands separators, blank input,
     * scientific notation such as {@code 1e3}, and values with no leading digit such as
     * {@code .50}.</p>
     *
     * <p>Parsing is exact: no floating-point arithmetic is involved. Examples: {@code 42 ->
     * 4200}, {@code 42.5 -> 4250}, {@code 0.00 -> 0}.</p>
     *
     * @param raw the text to parse
     * @return a {@code Money} for that amount
     * @throws IllegalArgumentException if {@code raw} is {@code null} or blank, does not match the
     *     accepted syntax, or is too large to represent in cents
     */
    public static Money parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("amount is required");
        }
        String s = raw.trim();
        if (!AMOUNT_PATTERN.matcher(s).matches()) {
            throw new IllegalArgumentException(
                    "invalid amount '" + raw
                            + "': expected a non-negative number with at most 2 decimal places");
        }
        try {
            String[] parts = s.split("\\.");
            long whole = Long.parseLong(parts[0]);
            long fraction = parts.length > 1
                    ? Long.parseLong(parts[1].length() == 1 ? parts[1] + "0" : parts[1])
                    : 0L;
            return new Money(Math.addExact(Math.multiplyExact(whole, 100L), fraction));
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("amount out of range: " + raw, e);
        }
    }

    /**
     * Adds {@code other} to this amount.
     *
     * @throws ArithmeticException if the result overflows a {@code long}
     */
    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    /**
     * Subtracts {@code other} from this amount. The result may be negative, for example when
     * computing a net total.
     *
     * @throws ArithmeticException if the result overflows a {@code long}
     */
    public Money minus(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    /**
     * Returns this amount with the opposite sign. The result may be negative, for example when
     * turning an expense magnitude into a net contribution.
     *
     * @throws ArithmeticException if the result overflows a {@code long}
     */
    public Money negate() {
        return new Money(Math.negateExact(cents));
    }

    public boolean isZero() {
        return cents == 0;
    }

    public boolean isNegative() {
        return cents < 0;
    }

    /**
     * Renders the amount with exactly two decimal places and no currency symbol, using
     * {@link java.util.Locale#ROOT} so the output is locale-independent.
     *
     * <p>Examples: {@code 4200 -> "42.00"}, {@code 4250 -> "42.50"}, {@code 0 -> "0.00"},
     * {@code -123456 -> "-1234.56"}.</p>
     */
    public String format() {
        long absolute = Math.abs(cents);
        long whole = absolute / 100;
        long fraction = absolute % 100;
        return (cents < 0 ? "-" : "") + whole + "." + (fraction < 10 ? "0" : "") + fraction;
    }

    /**
     * Renders the amount like {@link #format()}, but prefixes positive values with {@code "+"}.
     * Zero is rendered without a sign.
     *
     * <p>Examples: {@code 315770 -> "+3157.70"}, {@code -12000 -> "-120.00"},
     * {@code 0 -> "0.00"}.</p>
     */
    public String formatSigned() {
        if (cents > 0) {
            return "+" + format();
        }
        return format();
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    @Override
    public String toString() {
        return format();
    }
}
