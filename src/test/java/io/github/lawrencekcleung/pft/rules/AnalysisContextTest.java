package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalysisContextTest {

    private static Transaction transaction(int id, TransactionType type) {
        return new Transaction(id, LocalDate.of(2026, 11, 1), type, Money.ofCents(100),
                "groceries", "Transaction " + id);
    }

    @Test
    void expensesKeepOnlyExpensesInContextOrder() {
        Transaction firstExpense = transaction(1, TransactionType.EXPENSE);
        Transaction income = transaction(2, TransactionType.INCOME);
        Transaction secondExpense = transaction(3, TransactionType.EXPENSE);

        AnalysisContext ctx = new AnalysisContext(
                List.of(firstExpense, income, secondExpense), YearMonth.of(2026, 11));

        assertEquals(List.of(firstExpense, secondExpense), ctx.expenses());
    }

    @Test
    void periodIsRetained() {
        AnalysisContext ctx = new AnalysisContext(List.of(), YearMonth.of(2026, 11));

        assertEquals(YearMonth.of(2026, 11), ctx.period());
    }

    @Test
    void aNullPeriodMeansAllTime() {
        AnalysisContext ctx = new AnalysisContext(List.of(), null);

        assertNull(ctx.period());
    }

    @Test
    void theTransactionListIsCopied() {
        List<Transaction> source = new ArrayList<>();
        source.add(transaction(1, TransactionType.EXPENSE));

        AnalysisContext ctx = new AnalysisContext(source, null);
        source.add(transaction(2, TransactionType.EXPENSE));

        assertEquals(1, ctx.transactions().size());
    }
}
