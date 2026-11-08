package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Money;

import java.time.YearMonth;
import java.util.List;

/**
 * Totals for a period and the expense breakdown by category.
 *
 * @param period           the month, or {@code null} for all time
 * @param income           total income, non-negative
 * @param expenses         total expenses, non-negative
 * @param categories       expense categories, spend descending then name ascending
 * @param transactionCount how many transactions the period contained
 */
public record Summary(YearMonth period, Money income, Money expenses,
                      List<CategorySpend> categories, int transactionCount) {

    /**
     * Freezes the category list.
     */
    public Summary {
        categories = List.copyOf(categories);
    }

    /**
     * Returns income minus expenses.
     *
     * @return the signed net
     */
    public Money net() {
        return income.minus(expenses);
    }

    /**
     * Returns whether the period contained no transactions at all.
     *
     * @return {@code true} when there is nothing to summarise
     */
    public boolean isEmpty() {
        return transactionCount == 0;
    }
}
