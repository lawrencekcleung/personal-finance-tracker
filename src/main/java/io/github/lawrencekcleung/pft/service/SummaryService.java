package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.store.TransactionRepository;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Computes period totals and the expense breakdown.
 *
 * <p>Aggregation is exact: amounts are summed as {@code long} cents and percentages are integer
 * basis points. The service is read-only and free of {@code Clock} use — the period always comes
 * from the caller, so behaviour is deterministic.</p>
 */
public final class SummaryService {

    private static final long BASIS_POINTS = 10_000L;

    private final TransactionRepository repository;

    /**
     * Creates the service.
     *
     * @param repository the store to read
     */
    public SummaryService(TransactionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
    }

    /**
     * Summarises one month, or all time.
     *
     * @param month the month, or {@code null} for all time
     * @return the totals and category breakdown
     */
    public Summary summarize(YearMonth month) {
        List<Transaction> selected = repository.findAll().stream()
                .filter(tx -> month == null || YearMonth.from(tx.date()).equals(month))
                .toList();

        long income = selected.stream()
                .filter(Transaction::isIncome)
                .mapToLong(tx -> tx.amount().cents())
                .sum();
        long expenses = selected.stream()
                .filter(Transaction::isExpense)
                .mapToLong(tx -> tx.amount().cents())
                .sum();

        Map<String, Long> spendByCategory = selected.stream()
                .filter(Transaction::isExpense)
                .collect(Collectors.groupingBy(Transaction::category, TreeMap::new,
                        Collectors.summingLong(tx -> tx.amount().cents())));

        List<CategorySpend> categories = spendByCategory.entrySet().stream()
                .map(entry -> new CategorySpend(entry.getKey(),
                        Money.ofCents(entry.getValue()),
                        basisPoints(entry.getValue(), expenses)))
                .sorted(Comparator.comparing(CategorySpend::spend).reversed()
                        .thenComparing(CategorySpend::category))
                .toList();

        return new Summary(month, Money.ofCents(income), Money.ofCents(expenses),
                categories, selected.size());
    }

    private static long basisPoints(long spend, long totalExpenses) {
        return totalExpenses == 0 ? 0 : spend * BASIS_POINTS / totalExpenses;
    }
}
