package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.service.CategorySpend;
import io.github.lawrencekcleung.pft.service.Summary;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SummaryFormatterTest {

    private final SummaryFormatter formatter = new SummaryFormatter();

    private static Summary novemberFixture() {
        return new Summary(YearMonth.of(2026, 11),
                Money.ofCents(500000), Money.ofCents(18460),
                List.of(
                        new CategorySpend("groceries", Money.ofCents(11090), 6007),
                        new CategorySpend("client-entertainment", Money.ofCents(2610), 1413),
                        new CategorySpend("dining", Money.ofCents(1980), 1072),
                        new CategorySpend("transport", Money.ofCents(1580), 855),
                        new CategorySpend("subscriptions", Money.ofCents(1200), 650)),
                8);
    }

    @Test
    void rendersThePrdBlock() {
        assertEquals("""
                Period: 2026-11   Income: 5000.00   Expenses: 184.60   Net: +4815.40

                Category                     Spend     Share
                groceries                   110.90     60.1%
                client-entertainment         26.10     14.1%
                dining                       19.80     10.7%
                transport                    15.80      8.6%
                subscriptions                12.00      6.5%""",
                formatter.format(novemberFixture()));
    }

    @Test
    void rendersAllTimePeriod() {
        Summary summary = new Summary(null, Money.ofCents(100), Money.ZERO, List.of(), 1);

        assertEquals("Period: all time   Income: 1.00   Expenses: 0.00   Net: +1.00",
                formatter.format(summary));
    }

    @Test
    void signsANegativeNet() {
        Summary summary = new Summary(YearMonth.of(2026, 11),
                Money.ofCents(10000), Money.ofCents(20000), List.of(), 2);

        assertEquals("Period: 2026-11   Income: 100.00   Expenses: 200.00   Net: -100.00",
                formatter.format(summary));
    }

    @Test
    void rendersAnEmptyState() {
        Summary summary = new Summary(YearMonth.of(2026, 12),
                Money.ZERO, Money.ZERO, List.of(), 0);

        assertEquals("No transactions.", formatter.format(summary));
    }

    @Test
    void incomeOnlyOmitsTheCategoryTable() {
        Summary summary = new Summary(YearMonth.of(2026, 11),
                Money.ofCents(500000), Money.ZERO, List.of(), 1);

        String text = formatter.format(summary);

        assertEquals("Period: 2026-11   Income: 5000.00   Expenses: 0.00   Net: +5000.00",
                text);
        assertFalse(text.contains("Category"));
    }
}
