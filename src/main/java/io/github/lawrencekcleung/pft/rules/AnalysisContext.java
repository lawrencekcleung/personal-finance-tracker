package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Transaction;

import java.time.YearMonth;
import java.util.List;

/**
 * The transactions an {@link AnalysisRule} evaluates, and the period they represent.
 *
 * <p>The caller filters by month before building the context; {@code period} is then kept for the
 * rule's message. A {@code null} period means all time.</p>
 *
 * @param transactions the transactions to inspect, in the caller's order
 * @param period       the month under analysis, or {@code null} for all time
 */
public record AnalysisContext(List<Transaction> transactions, YearMonth period) {

    /**
     * Freezes the transaction list.
     */
    public AnalysisContext {
        transactions = List.copyOf(transactions);
    }

    /**
     * Returns only the expenses, preserving the context order.
     *
     * @return the expense transactions
     */
    public List<Transaction> expenses() {
        return transactions.stream()
                .filter(Transaction::isExpense)
                .toList();
    }
}
