package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.model.Category;
import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import io.github.lawrencekcleung.pft.store.TransactionRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Use cases that operate on transactions.
 *
 * <p>The service owns validation order and the list pipeline, and only ever sees the
 * {@link TransactionRepository} seam, never a file path. Domain violations are reported as
 * {@link DataException} (exit {@code 2}); storage failures pass through as
 * {@code StorageException}.</p>
 */
public final class TransactionService {

    private static final Comparator<Transaction> DISPLAY_ORDER =
            Comparator.comparing(Transaction::date).thenComparingInt(Transaction::id);

    private final TransactionRepository repository;

    /**
     * Creates the service over a repository.
     *
     * @param repository the store to read and write
     */
    public TransactionService(TransactionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
    }

    /**
     * Validates and stores one transaction.
     *
     * <p>Validation order is deliberate: the type is resolved first, then the record invariants
     * (amount, description, category), and only then is anything written.</p>
     *
     * @param date        the day of the transaction
     * @param type        the wire name, {@code income} or {@code expense}
     * @param amount      the non-negative magnitude
     * @param description what the transaction was for
     * @param category    the category, or {@code null} for {@link Category#UNCATEGORIZED}
     * @return the stored transaction, including its assigned id
     * @throws DataException if the type is unknown or the record is invalid
     */
    public Transaction add(LocalDate date, String type, Money amount, String description,
                           String category) {
        Objects.requireNonNull(date, "date is required");
        TransactionType parsedType = parseType(type);
        Transaction draft;
        try {
            draft = new Transaction(0, date, parsedType, amount, category, description);
        } catch (IllegalArgumentException e) {
            throw new DataException(e.getMessage(), e);
        }
        return repository.insert(draft);
    }

    /**
     * Removes a transaction. Its id is never reused.
     *
     * @param id the transaction id
     * @throws StorageException if the id is unknown
     */
    public void delete(int id) {
        repository.deleteById(id);
    }

    /**
     * Returns transactions in display order: date ascending, then id ascending.
     *
     * @param month             only this month, or {@code null} for all time
     * @param category          only this category, or {@code null} for any
     * @param uncategorizedOnly only transactions with no category, ignoring {@code category}
     * @return an immutable, sorted list
     */
    public List<Transaction> list(YearMonth month, String category, boolean uncategorizedOnly) {
        String wanted = category == null ? null : category.trim();
        return repository.findAll().stream()
                .filter(tx -> month == null || YearMonth.from(tx.date()).equals(month))
                .filter(tx -> matchesCategory(tx, wanted, uncategorizedOnly))
                .sorted(DISPLAY_ORDER)
                .toList();
    }

    private static boolean matchesCategory(Transaction tx, String category,
                                           boolean uncategorizedOnly) {
        if (uncategorizedOnly) {
            return Category.UNCATEGORIZED.equals(tx.category());
        }
        return category == null || category.equals(tx.category());
    }

    private static TransactionType parseType(String type) {
        try {
            return TransactionType.fromWire(type);
        } catch (IllegalArgumentException e) {
            throw new DataException(e.getMessage(), e);
        }
    }
}
