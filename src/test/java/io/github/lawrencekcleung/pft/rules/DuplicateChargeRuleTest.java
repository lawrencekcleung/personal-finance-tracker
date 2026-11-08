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

class DuplicateChargeRuleTest {

    private final DuplicateChargeRule rule = new DuplicateChargeRule(3);

    private static Transaction expense(int id, String date, String amount, String description) {
        return new Transaction(id, LocalDate.parse(date), TransactionType.EXPENSE,
                Money.parse(amount), "dining", description);
    }

    private static Transaction income(int id, String date, String amount, String description) {
        return new Transaction(id, LocalDate.parse(date), TransactionType.INCOME,
                Money.parse(amount), "income", description);
    }

    private static AnalysisContext context(Transaction... transactions) {
        return new AnalysisContext(List.of(transactions), YearMonth.of(2026, 11));
    }

    @Test
    void flagsAOneDayPairWithTheCanonicalMessage() {
        Finding finding = rule.evaluate(context(
                expense(8, "2026-11-06", "9.90", "Coffee"),
                expense(9, "2026-11-07", "9.90", "Coffee"))).get(0);

        assertEquals("duplicate-charge", finding.ruleId());
        assertEquals(List.of(8, 9), finding.transactionIds());
        assertEquals("9.90 \"Coffee\" on 2026-11-06 and 2026-11-07 (1 day apart)",
                finding.message());
    }

    @Test
    void threeDaysIsInsideTheWindow() {
        List<Finding> findings = rule.evaluate(context(
                expense(1, "2026-11-01", "5.00", "Lunch"),
                expense(2, "2026-11-04", "5.00", "Lunch")));

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).message().endsWith("(3 days apart)"));
    }

    @Test
    void fourDaysIsOutsideTheWindow() {
        assertTrue(rule.evaluate(context(
                expense(1, "2026-11-01", "5.00", "Lunch"),
                expense(2, "2026-11-05", "5.00", "Lunch"))).isEmpty());
    }

    @Test
    void sameDayIsAPair() {
        Finding finding = rule.evaluate(context(
                expense(1, "2026-11-01", "5.00", "Lunch"),
                expense(2, "2026-11-01", "5.00", "Lunch"))).get(0);

        assertEquals("5.00 \"Lunch\" on 2026-11-01 and 2026-11-01 (0 days apart)",
                finding.message());
    }

    @Test
    void differentAmountsAreNotDuplicates() {
        assertTrue(rule.evaluate(context(
                expense(1, "2026-11-01", "5.00", "Lunch"),
                expense(2, "2026-11-02", "6.00", "Lunch"))).isEmpty());
    }

    @Test
    void differentDescriptionsAreNotDuplicates() {
        assertTrue(rule.evaluate(context(
                expense(1, "2026-11-01", "5.00", "Lunch"),
                expense(2, "2026-11-02", "5.00", "Dinner"))).isEmpty());
    }

    @Test
    void normalizationIgnoresCaseAndExtraWhitespace() {
        List<Finding> findings = rule.evaluate(context(
                expense(1, "2026-11-01", "5.00", "Coffee  Shop"),
                expense(2, "2026-11-02", "5.00", " coffee shop ")));

        assertEquals(1, findings.size());
        assertEquals("5.00 \"Coffee  Shop\" on 2026-11-01 and 2026-11-02 (1 day apart)",
                findings.get(0).message());
    }

    @Test
    void incomeIsNeverConsidered() {
        assertTrue(rule.evaluate(context(
                income(1, "2026-11-01", "5000.00", "Salary"),
                income(2, "2026-11-02", "5000.00", "Salary"))).isEmpty());
    }

    @Test
    void threeIdenticalChargesProduceThreePairsInIdOrder() {
        List<Finding> findings = rule.evaluate(context(
                expense(1, "2026-11-01", "5.00", "Coffee"),
                expense(2, "2026-11-02", "5.00", "Coffee"),
                expense(3, "2026-11-03", "5.00", "Coffee")));

        assertEquals(List.of(List.of(1, 2), List.of(1, 3), List.of(2, 3)),
                findings.stream().map(Finding::transactionIds).toList());
    }

    @Test
    void pairsSortByEarliestDateThenIds() {
        List<Finding> findings = rule.evaluate(context(
                expense(1, "2026-11-10", "5.00", "Coffee"),
                expense(2, "2026-11-10", "5.00", "Coffee"),
                expense(3, "2026-11-01", "5.00", "Tea"),
                expense(4, "2026-11-01", "5.00", "Tea")));

        assertEquals(List.of(List.of(3, 4), List.of(1, 2)),
                findings.stream().map(Finding::transactionIds).toList());
    }

    @Test
    void theWindowIsConfigurable() {
        DuplicateChargeRule oneDay = new DuplicateChargeRule(1);

        assertTrue(oneDay.evaluate(context(
                expense(1, "2026-11-01", "5.00", "Lunch"),
                expense(2, "2026-11-03", "5.00", "Lunch"))).isEmpty());
    }

    @Test
    void identityDescribesTheWindow() {
        assertEquals("duplicate-charge", rule.id());
        assertEquals("flags equal expense charges within 3 days", rule.description());
    }

    @Test
    void aNegativeWindowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DuplicateChargeRule(-1));
    }
}
