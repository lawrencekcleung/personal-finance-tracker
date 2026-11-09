package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetRuleTest {

    private final BudgetRule rule = new BudgetRule(Money.ofCents(50_000));

    private static Transaction expense(int id, String date, String amount) {
        return new Transaction(id, LocalDate.parse(date), TransactionType.EXPENSE,
                Money.parse(amount), "groceries", "Transaction " + id);
    }

    private static Transaction income(int id, String date, String amount) {
        return new Transaction(id, LocalDate.parse(date), TransactionType.INCOME,
                Money.parse(amount), "income", "Transaction " + id);
    }

    private static AnalysisContext context(YearMonth period, Transaction... transactions) {
        return new AnalysisContext(List.of(transactions), period);
    }

    @Test
    void flagsAMonthOverBudget() {
        Finding finding = rule.evaluate(context(YearMonth.of(2026, 11),
                expense(1, "2026-11-01", "600.00"))).get(0);

        assertEquals("budget-exceeded", finding.ruleId());
        assertEquals(List.of(), finding.transactionIds());
        assertEquals("2026-11 expenses total 600.00, above the 500.00 monthly budget",
                finding.message());
    }

    @Test
    void exactlyAtTheBudgetIsNotFlagged() {
        assertTrue(rule.evaluate(context(YearMonth.of(2026, 11),
                expense(1, "2026-11-01", "500.00"))).isEmpty());
    }

    @Test
    void incomeDoesNotCountTowardsTheBudget() {
        assertTrue(rule.evaluate(context(YearMonth.of(2026, 11),
                income(1, "2026-11-01", "10000.00"),
                expense(2, "2026-11-02", "400.00"))).isEmpty());
    }

    @Test
    void reportsEveryOverBudgetMonthInChronologicalOrder() {
        List<Finding> findings = rule.evaluate(context(null,
                expense(1, "2026-11-01", "600.00"),
                expense(2, "2026-10-01", "100.00"),
                expense(3, "2026-12-01", "700.00")));

        assertEquals(List.of(
                "2026-11 expenses total 600.00, above the 500.00 monthly budget",
                "2026-12 expenses total 700.00, above the 500.00 monthly budget"),
                findings.stream().map(Finding::message).toList());
    }

    @Test
    void aPeriodContextOnlyReportsThatMonth() {
        List<Finding> findings = rule.evaluate(context(YearMonth.of(2026, 11),
                expense(1, "2026-11-01", "600.00"),
                expense(2, "2026-12-01", "700.00")));

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).message().startsWith("2026-11"));
    }

    @Test
    void theMessageUsesTheConfiguredBudget() {
        BudgetRule small = new BudgetRule(Money.ofCents(10_000));

        assertEquals("2026-11 expenses total 150.00, above the 100.00 monthly budget",
                small.evaluate(context(YearMonth.of(2026, 11),
                        expense(1, "2026-11-01", "150.00"))).get(0).message());
    }

    @Test
    void identityIsStable() {
        assertEquals("budget-exceeded", rule.id());
        assertEquals("flags months whose expenses exceed the monthly budget",
                rule.description());
        assertEquals(Money.ofCents(50_000), rule.monthlyBudget());
    }

    @Test
    void aNullBudgetIsRejected() {
        assertThrows(NullPointerException.class, () -> new BudgetRule(null));
    }
}
