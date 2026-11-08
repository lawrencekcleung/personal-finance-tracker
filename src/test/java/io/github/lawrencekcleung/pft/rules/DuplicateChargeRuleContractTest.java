package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Runs the analysis-rule contract against the duplicate-charge rule. */
class DuplicateChargeRuleContractTest extends AnalysisRuleContractTest {

    private static final Transaction INCOME = new Transaction(1, LocalDate.of(2026, 11, 1),
            TransactionType.INCOME, Money.ofCents(500000), "income", "Salary");

    private static final Transaction FIRST_CHARGE = new Transaction(2,
            LocalDate.of(2026, 11, 6), TransactionType.EXPENSE, Money.ofCents(990),
            "dining", "Coffee");

    private static final Transaction SECOND_CHARGE = new Transaction(3,
            LocalDate.of(2026, 11, 7), TransactionType.EXPENSE, Money.ofCents(990),
            "dining", "Coffee");

    private static final Transaction UNIQUE = new Transaction(4, LocalDate.of(2026, 11, 8),
            TransactionType.EXPENSE, Money.ofCents(4250), "groceries", "Countdown");

    @Override
    protected AnalysisRule rule() {
        return new DuplicateChargeRule(3);
    }

    @Override
    protected AnalysisContext context() {
        return new AnalysisContext(List.of(INCOME, FIRST_CHARGE, SECOND_CHARGE, UNIQUE),
                YearMonth.of(2026, 11));
    }
}
