package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.model.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsedArgsTest {

    private static final OptionSpec TEXT =
            new OptionSpec("text", "some text", OptionType.STRING, false, null);
    private static final OptionSpec DATE =
            new OptionSpec("date", "the day", OptionType.DATE, false, null);
    private static final OptionSpec AMOUNT =
            new OptionSpec("amount", "how much", OptionType.AMOUNT, false, null);
    private static final OptionSpec MONTH =
            new OptionSpec("month", "the month", OptionType.YEAR_MONTH, false, null);
    private static final OptionSpec APPLY =
            new OptionSpec("apply", "apply it", OptionType.FLAG, false, null);

    private static ParsedArgs parse(List<OptionSpec> specs, String... tokens) {
        return new ArgumentParser().parse(specs, List.of(tokens));
    }

    @Test
    void requireStringReturnsTheValue() {
        assertEquals("hello", parse(List.of(TEXT), "--text", "hello").requireString("text"));
    }

    @Test
    void optionalStringIsEmptyWhenAbsent() {
        assertTrue(parse(List.of(TEXT)).optionalString("text").isEmpty());
    }

    @Test
    void optionalStringReturnsTheValueWhenPresent() {
        assertEquals("hello", parse(List.of(TEXT), "--text=hello")
                .optionalString("text").orElseThrow());
    }

    @Test
    void requireAmountReturnsMoney() {
        assertEquals(Money.ofCents(4250),
                parse(List.of(AMOUNT), "--amount", "42.50").requireAmount("amount"));
    }

    @Test
    void requireDateReturnsLocalDate() {
        assertEquals(LocalDate.of(2026, 11, 1),
                parse(List.of(DATE), "--date", "2026-11-01").requireDate("date"));
    }

    @Test
    void optionalMonthReturnsTheMonthOrEmpty() {
        assertEquals(YearMonth.of(2026, 11),
                parse(List.of(MONTH), "--month", "2026-11")
                        .optionalMonth("month").orElseThrow());
        assertTrue(parse(List.of(MONTH)).optionalMonth("month").isEmpty());
    }

    @Test
    void flagReflectsPresence() {
        assertTrue(parse(List.of(APPLY), "--apply").flag("apply"));
        assertFalse(parse(List.of(APPLY)).flag("apply"));
    }

    @Test
    void askingForTheWrongTypeIsAProgrammingError() {
        ParsedArgs args = parse(List.of(DATE), "--date", "2026-11-01");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> args.requireAmount("date"));

        assertEquals("--date is a DATE option, not AMOUNT", e.getMessage());
    }

    @Test
    void requiringAnAbsentOptionIsAProgrammingError() {
        ParsedArgs args = parse(List.of(TEXT));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> args.requireString("text"));

        assertEquals("--text is required but was not provided", e.getMessage());
    }

    @Test
    void askingForAnUndeclaredOptionIsAProgrammingError() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse(List.of(TEXT)).requireString("ghost"));

        assertEquals("unknown option --ghost", e.getMessage());
    }
}
