package io.github.lawrencekcleung.pft.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Transaction} and {@link TransactionType}: validation, normalization and
 * wire-name handling.
 */
class TransactionTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @Test
    void validConstructionKeepsValuesAndTrimsDescription() {
        Transaction transaction = new Transaction(
                7, DATE, TransactionType.EXPENSE, Money.ofCents(4250), "  groceries  ", "  weekly shop  ");

        assertEquals(7, transaction.id());
        assertEquals(DATE, transaction.date());
        assertEquals(TransactionType.EXPENSE, transaction.type());
        assertEquals(Money.ofCents(4250), transaction.amount());
        assertEquals("groceries", transaction.category());
        assertEquals("weekly shop", transaction.description());
    }

    @Test
    void missingCategoryDefaultsToUncategorized() {
        Transaction transaction = new Transaction(
                1, DATE, TransactionType.INCOME, Money.ofCents(315_770), null, "salary");

        assertEquals(Category.UNCATEGORIZED, transaction.category());
    }

    @Test
    void blankCategoryDefaultsToUncategorized() {
        Transaction transaction = new Transaction(
                1, DATE, TransactionType.INCOME, Money.ofCents(315_770), "   ", "salary");

        assertEquals(Category.UNCATEGORIZED, transaction.category());
    }

    @Test
    void blankDescriptionIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new Transaction(1, DATE, TransactionType.EXPENSE, Money.ofCents(100), null, "   "));

        assertEquals("description is required", error.getMessage());
    }

    @Test
    void nullDescriptionIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new Transaction(1, DATE, TransactionType.EXPENSE, Money.ofCents(100), null, null));
    }

    @Test
    void nullDateIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new Transaction(1, null, TransactionType.EXPENSE, Money.ofCents(100), null, "lunch"));
    }

    @Test
    void nullTypeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new Transaction(1, DATE, null, Money.ofCents(100), null, "lunch"));
    }

    @Test
    void nullAmountIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new Transaction(1, DATE, TransactionType.EXPENSE, null, null, "lunch"));
    }

    @Test
    void negativeAmountIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new Transaction(1, DATE, TransactionType.EXPENSE, Money.ofCents(-100), null, "lunch"));

        assertEquals("amount must be non-negative; use type for direction", error.getMessage());
    }

    @Test
    void isExpenseAndIsIncomeDisagree() {
        Transaction expense = new Transaction(1, DATE, TransactionType.EXPENSE, Money.ofCents(100), null, "lunch");
        Transaction income = new Transaction(2, DATE, TransactionType.INCOME, Money.ofCents(100), null, "refund");

        assertTrue(expense.isExpense());
        assertFalse(expense.isIncome());
        assertTrue(income.isIncome());
        assertFalse(income.isExpense());
    }

    @Test
    void transactionTypeFromWireRoundTrips() {
        for (TransactionType type : TransactionType.values()) {
            assertEquals(type, TransactionType.fromWire(type.wireName()));
        }
        assertEquals("income", TransactionType.INCOME.wireName());
        assertEquals("expense", TransactionType.EXPENSE.wireName());
    }

    @Test
    void transactionTypeFromWireRejectsUnknownValues() {
        assertEquals("unknown transaction type: null",
                assertThrows(IllegalArgumentException.class, () -> TransactionType.fromWire(null)).getMessage());
        assertEquals("unknown transaction type: Income",
                assertThrows(IllegalArgumentException.class, () -> TransactionType.fromWire("Income")).getMessage());
        assertEquals("unknown transaction type: unknown",
                assertThrows(IllegalArgumentException.class, () -> TransactionType.fromWire("unknown")).getMessage());
    }
}
