package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Money;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgumentParserTest {

    private static final OptionSpec DATE =
            new OptionSpec("date", "the day", OptionType.DATE, true, null);
    private static final OptionSpec AMOUNT =
            new OptionSpec("amount", "how much", OptionType.AMOUNT, false, null);
    private static final OptionSpec MONTH =
            new OptionSpec("month", "the month", OptionType.YEAR_MONTH, false, "2026-11");
    private static final OptionSpec ID =
            new OptionSpec("id", "the id", OptionType.INT, false, null);
    private static final OptionSpec DATA =
            new OptionSpec("data", "the file", OptionType.PATH, false, null);
    private static final OptionSpec APPLY =
            new OptionSpec("apply", "apply the change", OptionType.FLAG, false, null);

    private static ParsedArgs parse(List<OptionSpec> specs, String... tokens) {
        return new ArgumentParser().parse(specs, List.of(tokens));
    }

    @Test
    void acceptsSpaceSeparatedValues() {
        ParsedArgs args = parse(List.of(DATE), "--date", "2026-11-01");

        assertEquals(LocalDate.of(2026, 11, 1), args.requireDate("date"));
    }

    @Test
    void acceptsEqualsSeparatedValues() {
        ParsedArgs args = parse(List.of(DATE), "--date=2026-11-02");

        assertEquals(LocalDate.of(2026, 11, 2), args.requireDate("date"));
    }

    @Test
    void parsesEveryTypeEagerly() {
        ParsedArgs args = parse(List.of(DATE, AMOUNT, MONTH, ID, DATA, APPLY),
                "--date", "2026-11-01",
                "--amount=42.50",
                "--id", "7",
                "--data", "data/transactions.csv",
                "--apply");

        assertEquals(LocalDate.of(2026, 11, 1), args.requireDate("date"));
        assertEquals(Money.ofCents(4250), args.requireAmount("amount"));
        assertEquals(YearMonth.of(2026, 11), args.optionalMonth("month").orElseThrow());
        assertEquals(7, args.requireInt("id"));
        assertEquals(Path.of("data/transactions.csv"), args.optionalPath("data").orElseThrow());
        assertTrue(args.flag("apply"));
    }

    @Test
    void absentFlagIsFalse() {
        assertFalse(parse(List.of(APPLY)).flag("apply"));
    }

    @Test
    void unknownOptionListsTheValidOnes() {
        UsageException e = assertThrows(UsageException.class,
                () -> parse(List.of(DATE, APPLY), "--nope"));

        assertEquals("unknown option --nope; valid options: --date, --apply", e.getMessage());
    }

    @Test
    void unknownOptionWhenNoneAreDeclared() {
        UsageException e = assertThrows(UsageException.class,
                () -> parse(List.of(), "--nope"));

        assertEquals("unknown option --nope; valid options: none", e.getMessage());
    }

    @Test
    void missingRequiredOptionIsReported() {
        UsageException e = assertThrows(UsageException.class,
                () -> parse(List.of(DATE, APPLY), "--apply"));

        assertEquals("missing required option --date", e.getMessage());
    }

    @Test
    void conversionFailureUsesTheConsistentMessage() {
        UsageException e = assertThrows(UsageException.class,
                () -> parse(List.of(DATE, AMOUNT), "--date", "01/11/2026", "--amount", "1"));

        assertEquals("--date: expected yyyy-MM-dd", e.getMessage());
    }

    @Test
    void optionAtTheEndWithoutValueIsReported() {
        UsageException e = assertThrows(UsageException.class,
                () -> parse(List.of(DATE, AMOUNT), "--date"));

        assertEquals("--date: missing value", e.getMessage());
    }

    @Test
    void aFollowingOptionMeansTheValueIsMissing() {
        UsageException e = assertThrows(UsageException.class,
                () -> parse(List.of(DATE, AMOUNT), "--date", "--amount", "1.00"));

        assertEquals("--date: missing value", e.getMessage());
    }

    @Test
    void duplicateOptionIsRejected() {
        UsageException e = assertThrows(UsageException.class,
                () -> parse(List.of(DATE), "--date", "2026-11-01", "--date", "2026-11-02"));

        assertEquals("--date: specified more than once", e.getMessage());
    }

    @Test
    void flagWithAnInlineValueIsRejected() {
        UsageException e = assertThrows(UsageException.class,
                () -> parse(List.of(APPLY), "--apply=true"));

        assertEquals("--apply: flag takes no value", e.getMessage());
    }

    @Test
    void defaultsAreAppliedAndConverted() {
        assertEquals(YearMonth.of(2026, 11), parse(List.of(MONTH)).optionalMonth("month")
                .orElseThrow());
    }

    @Test
    void positionalArgumentsAreRejected() {
        UsageException e = assertThrows(UsageException.class,
                () -> parse(List.of(DATE), "2026-11-01"));

        assertEquals("unexpected argument '2026-11-01'", e.getMessage());
    }
}
