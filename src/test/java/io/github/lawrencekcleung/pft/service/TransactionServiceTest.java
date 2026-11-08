package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.model.Category;
import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import io.github.lawrencekcleung.pft.store.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionServiceTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    private InMemoryTransactionRepository repository;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        service = new TransactionService(repository);
    }

    private Transaction addExpense(String description) {
        return service.add(NOV_1, "expense", Money.ofCents(4250), description, "groceries");
    }

    // -------------------------------------------------------------------- add

    @Test
    void addStoresTheTransactionWithAnAssignedId() {
        Transaction stored = service.add(NOV_1, "expense", Money.ofCents(4250),
                "Countdown", "groceries");

        assertEquals(1, stored.id());
        assertEquals(TransactionType.EXPENSE, stored.type());
        assertEquals(Money.ofCents(4250), stored.amount());
        assertEquals("groceries", stored.category());
        assertEquals(List.of(stored), repository.findAll());
        assertEquals(1, repository.writeCount());
    }

    @Test
    void addDefaultsTheCategoryToUncategorized() {
        Transaction stored = service.add(NOV_1, "expense", Money.ofCents(100), "Shop", null);

        assertEquals(Category.UNCATEGORIZED, stored.category());
    }

    @Test
    void addTrimsTheDescription() {
        Transaction stored = service.add(NOV_1, "expense", Money.ofCents(100),
                "  Shop  ", null);

        assertEquals("Shop", stored.description());
    }

    @Test
    void addResolvesIncomeAndExpenseWireNames() {
        assertEquals(TransactionType.INCOME,
                service.add(NOV_1, "income", Money.ofCents(1), "Salary", null).type());
        assertEquals(TransactionType.EXPENSE,
                service.add(NOV_1, "expense", Money.ofCents(1), "Shop", null).type());
    }

    @Test
    void unknownTypeIsADataException() {
        DataException e = assertThrows(DataException.class,
                () -> service.add(NOV_1, "transfer", Money.ofCents(1), "Move", null));

        assertEquals("unknown transaction type: transfer", e.getMessage());
        assertTrue(repository.findAll().isEmpty(), "a rejected add must not write");
    }

    @Test
    void blankDescriptionIsADataException() {
        DataException e = assertThrows(DataException.class,
                () -> service.add(NOV_1, "expense", Money.ofCents(1), "   ", null));

        assertEquals("description is required", e.getMessage());
    }

    @Test
    void negativeAmountIsADataException() {
        DataException e = assertThrows(DataException.class,
                () -> service.add(NOV_1, "expense", Money.ofCents(-1), "Shop", null));

        assertEquals("amount must be non-negative; use type for direction", e.getMessage());
    }

    @Test
    void theTypeIsValidatedBeforeTheRecord() {
        DataException e = assertThrows(DataException.class,
                () -> service.add(NOV_1, "transfer", Money.ofCents(1), "   ", null));

        assertEquals("unknown transaction type: transfer", e.getMessage());
    }

    // ------------------------------------------------------------------- list

    @Test
    void listReturnsEverythingInDateThenIdOrder() {
        Transaction novemberSecond = service.add(LocalDate.of(2026, 11, 2), "expense",
                Money.ofCents(1), "Second", null);
        Transaction novemberFirst = addExpense("First");
        Transaction alsoNovemberFirst = service.add(NOV_1, "income", Money.ofCents(1),
                "Third", null);

        List<Transaction> listed = service.list(null, null, false);

        assertEquals(List.of(novemberFirst.id(), alsoNovemberFirst.id(), novemberSecond.id()),
                listed.stream().map(Transaction::id).toList());
    }

    @Test
    void listFiltersByMonth() {
        Transaction november = addExpense("November");
        service.add(LocalDate.of(2026, 12, 1), "expense", Money.ofCents(1), "December", null);

        assertEquals(List.of(november), service.list(YearMonth.of(2026, 11), null, false));
    }

    @Test
    void listFiltersByCategory() {
        Transaction groceries = addExpense("Groceries");
        service.add(NOV_1, "expense", Money.ofCents(1), "Dining", "dining");

        assertEquals(List.of(groceries), service.list(null, "groceries", false));
    }

    @Test
    void listTrimsTheCategoryFilter() {
        Transaction groceries = addExpense("Groceries");

        assertEquals(List.of(groceries), service.list(null, "  groceries  ", false));
    }

    @Test
    void listFiltersUncategorized() {
        service.add(NOV_1, "expense", Money.ofCents(1), "Categorized", "dining");
        Transaction plain = service.add(NOV_1, "expense", Money.ofCents(1), "Plain", null);

        assertEquals(List.of(plain), service.list(null, null, true));
    }

    @Test
    void listCombinesMonthAndCategoryFilters() {
        Transaction match = service.add(LocalDate.of(2026, 11, 1), "expense",
                Money.ofCents(1), "Match", "dining");
        Transaction sameMonth = service.add(LocalDate.of(2026, 11, 2), "expense",
                Money.ofCents(1), "Other", "dining");
        service.add(LocalDate.of(2026, 12, 1), "expense", Money.ofCents(1), "Month", "dining");

        assertEquals(List.of(match, sameMonth),
                service.list(YearMonth.of(2026, 11), "dining", false));
    }

    @Test
    void listReturnsEmptyWhenNothingMatches() {
        addExpense("Groceries");

        assertTrue(service.list(null, "transport", false).isEmpty());
    }
}
