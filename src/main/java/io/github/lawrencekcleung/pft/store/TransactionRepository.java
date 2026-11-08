package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistence seam for transactions.
 *
 * <p>Only {@link RepositoryFactory} knows about concrete formats; services depend on this
 * interface alone. Implementations must guarantee that ids are positive, unique and never reused
 * after a delete.</p>
 *
 * <p>All failures are reported as {@link StorageException}, so callers never see
 * {@code IOException} or {@code SQLException}.</p>
 */
public interface TransactionRepository extends AutoCloseable {

    /**
     * Returns every stored transaction. The order is unspecified; callers that need a stable
     * order must sort.
     *
     * @return an immutable list, empty when the store is empty
     */
    List<Transaction> findAll();

    /**
     * Looks up a single transaction by its id.
     *
     * @param id the repository-assigned id
     * @return the transaction, or empty when no such id exists
     */
    Optional<Transaction> findById(int id);

    /**
     * Persists a new transaction, assigning it the next id.
     *
     * @param draft the transaction to store; its id is ignored
     * @return the stored transaction, including its assigned id
     */
    Transaction insert(Transaction draft);

    /**
     * Replaces the stored transaction that has the same id.
     *
     * @param tx the replacement
     * @throws StorageException if the id is unknown
     */
    void update(Transaction tx);

    /**
     * Removes the transaction with the given id. The id is never reused afterwards.
     *
     * @param id the id to remove
     * @throws StorageException if the id is unknown
     */
    void deleteById(int id);

    /**
     * Persists several transactions in one call.
     *
     * <p>The default implementation loops over {@link #insert(Transaction)} and is therefore
     * <em>not</em> atomic. Adapters that can write atomically (a single file replacement, a single
     * database transaction) must override it.</p>
     *
     * @param drafts the transactions to store
     * @return the stored transactions with their assigned ids, in input order
     */
    default List<Transaction> insertAll(List<Transaction> drafts) {
        List<Transaction> saved = new ArrayList<>(drafts.size());
        for (Transaction draft : drafts) {
            saved.add(insert(draft));
        }
        return List.copyOf(saved);
    }

    /**
     * Releases any resources. A no-op for file-backed stores; JDBC-backed stores close their
     * connection here.
     */
    @Override
    default void close() {
        // nothing to release by default
    }
}
