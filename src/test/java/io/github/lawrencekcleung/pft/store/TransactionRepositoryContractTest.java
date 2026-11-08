package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.model.Category;
import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behavioural contract every {@link TransactionRepository} must satisfy.
 *
 * <p>One subclass per adapter supplies the fresh repository; the same nine cases then run against
 * CSV today and against SQLite when that adapter lands. Add a test here only when the contract
 * itself grows, never for an adapter quirk.</p>
 */
abstract class TransactionRepositoryContractTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 1);

    private TransactionRepository repository;

    /**
     * Creates an empty repository backed by the adapter under test.
     *
     * @return a repository with no transactions
     */
    protected abstract TransactionRepository newRepository();

    /** Returns the repository opened for the current test. */
    protected final TransactionRepository repository() {
        return repository;
    }

    @BeforeEach
    final void openRepository() {
        repository = newRepository();
    }

    @AfterEach
    final void closeRepository() {
        repository.close();
    }

    /** Creates a valid draft, whose id the store must ignore. */
    protected static Transaction draft(String description) {
        return new Transaction(0, DATE, TransactionType.EXPENSE, Money.ofCents(4250),
                "groceries", description);
    }

    @Test
    void emptyStoreReturnsNoTransactions() {
        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findById(1).isEmpty());
    }

    @Test
    void insertAssignsStrictlyIncreasingIds() {
        int first = repository.insert(draft("One")).id();
        int second = repository.insert(draft("Two")).id();
        int third = repository.insert(draft("Three")).id();

        assertTrue(first > 0, "ids are positive");
        assertTrue(second > first, "ids increase");
        assertTrue(third > second, "ids increase");
        assertEquals(3, repository.findAll().size());
    }

    @Test
    void findByIdMissReturnsEmpty() {
        Transaction stored = repository.insert(draft("One"));

        assertEquals(stored, repository.findById(stored.id()).orElseThrow());
        assertTrue(repository.findById(stored.id() + 1).isEmpty());
    }

    @Test
    void updateReplacesStoredValues() {
        Transaction stored = repository.insert(draft("One"));
        repository.insert(draft("Two"));

        Transaction updated = new Transaction(stored.id(), stored.date(), stored.type(),
                Money.ofCents(9999), "dining", "One edited");
        repository.update(updated);

        assertEquals(updated, repository.findById(stored.id()).orElseThrow());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void deleteRemovesAndNeverReusesId() {
        repository.insert(draft("One"));
        repository.insert(draft("Two"));
        Transaction third = repository.insert(draft("Three"));

        repository.deleteById(third.id());

        assertTrue(repository.findById(third.id()).isEmpty());
        int afterDelete = repository.insert(draft("Four")).id();
        assertNotEquals(third.id(), afterDelete, "the deleted id must not be reused");
        assertTrue(afterDelete > third.id(), "ids stay monotonic across deletes");
    }

    @Test
    void insertAllIsAllOrNothing() {
        List<Transaction> drafts = Arrays.asList(draft("One"), null);

        assertThrows(NullPointerException.class, () -> repository.insertAll(drafts));

        assertTrue(repository.findAll().isEmpty(), "a failed batch must persist nothing");
    }

    @Test
    void insertAllPersistsEveryRowAndReturnsThemInInputOrder() {
        List<Transaction> stored = repository.insertAll(
                List.of(draft("One"), draft("Two"), draft("Three")));

        assertEquals(3, stored.size());
        assertEquals("One", stored.get(0).description());
        assertEquals("Three", stored.get(2).description());
        for (Transaction tx : stored) {
            assertEquals(tx, repository.findById(tx.id()).orElseThrow());
        }
    }

    @Test
    void roundTripsZeroAndFiveMillion() {
        for (Money amount : List.of(Money.ofCents(0), Money.ofCents(5_000_000))) {
            Transaction stored = repository.insert(new Transaction(0, DATE,
                    TransactionType.INCOME, amount, "income", "Amount " + amount.format()));

            assertEquals(stored, repository.findById(stored.id()).orElseThrow());
        }
    }

    @Test
    void roundTripsCommaQuoteAndUnicodeInDescription() {
        Transaction original = new Transaction(0, LocalDate.of(2026, 11, 3),
                TransactionType.EXPENSE, Money.ofCents(123456), "eating, out",
                "Café, \"the best\"\nand cheapest");

        Transaction stored = repository.insert(original);

        assertEquals(stored, repository.findById(stored.id()).orElseThrow());
        assertEquals("Café, \"the best\"\nand cheapest", stored.description());
        assertEquals("eating, out", stored.category());
    }

    @Test
    void roundTripsUncategorizedValue() {
        Transaction stored = repository.insert(new Transaction(0, DATE,
                TransactionType.EXPENSE, Money.ofCents(100), "", "Shop"));

        assertEquals(Category.UNCATEGORIZED, stored.category());
        assertEquals(Category.UNCATEGORIZED,
                repository.findById(stored.id()).orElseThrow().category());
    }

    @Test
    void unknownUpdateAndDeleteAreStorageExceptions() {
        Transaction ghost = new Transaction(42, DATE, TransactionType.EXPENSE,
                Money.ofCents(1), "groceries", "Ghost");

        assertThrows(StorageException.class, () -> repository.update(ghost));
        assertThrows(StorageException.class, () -> repository.deleteById(42));
        assertTrue(repository.findAll().isEmpty());
    }
}
