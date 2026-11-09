package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Runs the shared analysis-rule contract against the budget rule. */
class BudgetRuleContractTest extends AnalysisRuleContractTest {

    private static final Transaction INCOME = new Transaction(1, LocalDate.of(2026, 11, 1),
            TransactionType.INCOME, Money.ofCents(500000), "income", "Salary");

    private static final Transaction RENT = new Transaction(2, LocalDate.of(2026, 11, 2),
            TransactionType.EXPENSE, Money.ofCents(120000), "rent", "November rent");

    private static final Transaction COFFEE = new Transaction(3, LocalDate.of(2026, 11, 3),
            TransactionType.EXPENSE, Money.ofCents(990), "dining", "Coffee");

    @Override
    protected AnalysisRule rule() {
        return new BudgetRule(Money.ofCents(50_000));
    }

    @Override
    protected AnalysisContext context() {
        return new AnalysisContext(List.of(INCOME, RENT, COFFEE),
                YearMonth.of(2026, 11));
    }
}
