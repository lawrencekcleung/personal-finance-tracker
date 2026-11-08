package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory {@link TransactionRepository} for service and CLI tests.
 *
 * <p>It behaves exactly like the CSV adapter as far as the contract is concerned — ids are
 * positive, monotonic and never reused, and {@link #insertAll} commits all rows or none — but it
 * never touches the disk. {@link #writeCount()} counts committing mutations so tests can assert
 * that read-only commands perform no writes.</p>
 */
public final class InMemoryTransactionRepository implements TransactionRepository {

    private final List<Transaction> transactions = new ArrayList<>();
    private int nextId = 1;
    private int writeCount;

    @Override
    public List<Transaction> findAll() {
        return List.copyOf(transactions);
    }

    @Override
    public Optional<Transaction> findById(int id) {
        return transactions.stream()
                .filter(tx -> tx.id() == id)
                .findFirst();
    }

    @Override
    public Transaction insert(Transaction draft) {
        Objects.requireNonNull(draft, "draft is required");
        Transaction stored = withId(nextId++, draft);
        transactions.add(stored);
        writeCount++;
        return stored;
    }

    @Override
    public List<Transaction> insertAll(List<Transaction> drafts) {
        Objects.requireNonNull(drafts, "drafts is required");
        if (drafts.isEmpty()) {
            return List.of();
        }
        // Stage the whole batch first so one bad draft leaves the store untouched.
        List<Transaction> staged = new ArrayList<>(drafts.size());
        int id = nextId;
        for (Transaction draft : drafts) {
            Objects.requireNonNull(draft, "draft is required");
            staged.add(withId(id++, draft));
        }
        transactions.addAll(staged);
        nextId = id;
        writeCount++;
        return List.copyOf(staged);
    }

    @Override
    public void update(Transaction tx) {
        Objects.requireNonNull(tx, "tx is required");
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).id() == tx.id()) {
                transactions.set(i, tx);
                writeCount++;
                return;
            }
        }
        throw new StorageException("unknown transaction id: " + tx.id());
    }

    @Override
    public void deleteById(int id) {
        boolean removed = transactions.removeIf(tx -> tx.id() == id);
        if (!removed) {
            throw new StorageException("unknown transaction id: " + id);
        }
        writeCount++;
    }

    /**
     * Returns how many mutations have been committed. Reads never change it.
     *
     * @return the number of successful inserts, updates and deletes
     */
    public int writeCount() {
        return writeCount;
    }

    private static Transaction withId(int id, Transaction draft) {
        return new Transaction(id, draft.date(), draft.type(), draft.amount(),
                draft.category(), draft.description());
    }
}
