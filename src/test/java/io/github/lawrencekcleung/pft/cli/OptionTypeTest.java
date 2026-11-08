package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Money;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionTypeTest {

    @Test
    void stringReturnsTheText() {
        assertEquals("hello", OptionType.STRING.convert("name", "hello"));
    }

    @Test
    void flagConvertsToTrue() {
        assertEquals(Boolean.TRUE, OptionType.FLAG.convert("apply", ""));
    }

    @Test
    void dateParsesIsoDate() {
        assertEquals(LocalDate.of(2026, 11, 1),
                OptionType.DATE.convert("date", "2026-11-01"));
    }

    @Test
    void amountParsesToCents() {
        assertEquals(Money.ofCents(4250), OptionType.AMOUNT.convert("amount", "42.50"));
    }

    @Test
    void yearMonthParsesMonth() {
        assertEquals(YearMonth.of(2026, 11),
                OptionType.YEAR_MONTH.convert("month", "2026-11"));
    }

    @Test
    void intParsesWholeNumber() {
        assertEquals(7, OptionType.INT.convert("id", "7"));
    }

    @Test
    void pathParsesToPath() {
        assertEquals(Path.of("data/transactions.csv"),
                OptionType.PATH.convert("data", "data/transactions.csv"));
    }

    @Test
    void badDateUsesTheConsistentMessage() {
        UsageException e = assertThrows(UsageException.class,
                () -> OptionType.DATE.convert("date", "01/11/2026"));

        assertEquals("--date: expected yyyy-MM-dd", e.getMessage());
    }

    @Test
    void badAmountUsesTheConsistentMessage() {
        UsageException e = assertThrows(UsageException.class,
                () -> OptionType.AMOUNT.convert("amount", "42.505"));

        assertEquals("--amount: expected an amount with at most 2 decimals", e.getMessage());
    }

    @Test
    void badMonthAndBadIntUseTheConsistentMessage() {
        assertEquals("--month: expected yyyy-MM",
                assertThrows(UsageException.class,
                        () -> OptionType.YEAR_MONTH.convert("month", "2026-13")).getMessage());
        assertEquals("--id: expected an integer",
                assertThrows(UsageException.class,
                        () -> OptionType.INT.convert("id", "seven")).getMessage());
    }

    @Test
    void valueLabelsMatchTheHelpPlaceholders() {
        assertEquals("yyyy-MM-dd", OptionType.DATE.valueLabel());
        assertEquals("amount", OptionType.AMOUNT.valueLabel());
        assertEquals("", OptionType.FLAG.valueLabel());
        assertTrue(OptionType.PATH.valueLabel().length() > 0);
    }
}
