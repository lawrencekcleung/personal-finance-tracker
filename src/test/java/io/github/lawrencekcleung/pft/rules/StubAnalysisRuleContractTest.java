package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Proves the contract suite itself is green against a minimal rule. */
class StubAnalysisRuleContractTest extends AnalysisRuleContractTest {

    private static final Transaction INCOME = new Transaction(1, LocalDate.of(2026, 11, 1),
            TransactionType.INCOME, Money.ofCents(500000), "income", "Salary");

    private static final Transaction EXPENSE = new Transaction(2, LocalDate.of(2026, 11, 1),
            TransactionType.EXPENSE, Money.ofCents(4250), "groceries", "Countdown");

    private static final Transaction SECOND_EXPENSE = new Transaction(3, LocalDate.of(2026, 11, 2),
            TransactionType.EXPENSE, Money.ofCents(990), "dining", "Coffee");

    @Override
    protected AnalysisRule rule() {
        return new StubRule();
    }

    @Override
    protected AnalysisContext context() {
        return new AnalysisContext(List.of(INCOME, EXPENSE, SECOND_EXPENSE),
                YearMonth.of(2026, 11));
    }

    private static final class StubRule implements AnalysisRule {

        @Override
        public String id() {
            return "stub";
        }

        @Override
        public String description() {
            return "returns one finding per expense";
        }

        @Override
        public List<Finding> evaluate(AnalysisContext ctx) {
            return ctx.expenses().stream()
                    .map(tx -> new Finding(id(), List.of(tx.id()),
                            "Stub saw " + tx.description()))
                    .toList();
        }
    }
}
