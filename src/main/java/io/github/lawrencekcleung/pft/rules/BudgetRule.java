package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Money;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Flags months whose expenses exceed the configured monthly budget.
 *
 * <p>Read-only: it inspects the context's expenses only, groups them by calendar month, and emits
 * one finding per month whose total is strictly above the budget. Income is ignored. With a
 * period context only that month is considered; without one, every month in the context is
 * reported in chronological order.</p>
 */
public final class BudgetRule implements AnalysisRule {

    private final Money monthlyBudget;

    /**
     * Creates the rule.
     *
     * @param monthlyBudget the monthly expense limit
     */
    public BudgetRule(Money monthlyBudget) {
        this.monthlyBudget = Objects.requireNonNull(monthlyBudget, "monthlyBudget is required");
    }

    @Override
    public String id() {
        return "budget-exceeded";
    }

    @Override
    public String description() {
        return "flags months whose expenses exceed the monthly budget";
    }

    @Override
    public List<Finding> evaluate(AnalysisContext ctx) {
        Map<YearMonth, Long> expensesByMonth = ctx.expenses().stream()
                .filter(tx -> ctx.period() == null
                        || YearMonth.from(tx.date()).equals(ctx.period()))
                .collect(Collectors.groupingBy(tx -> YearMonth.from(tx.date()),
                        TreeMap::new,
                        Collectors.summingLong(tx -> tx.amount().cents())));

        return expensesByMonth.entrySet().stream()
                .filter(entry -> entry.getValue() > monthlyBudget.cents())
                .map(entry -> new Finding(id(), List.of(),
                        entry.getKey() + " expenses total "
                                + Money.ofCents(entry.getValue()).format()
                                + ", above the " + monthlyBudget.format()
                                + " monthly budget"))
                .toList();
    }

    /**
     * Returns the configured budget, for diagnostics and tests.
     *
     * @return the monthly limit
     */
    public Money monthlyBudget() {
        return monthlyBudget;
    }
}
