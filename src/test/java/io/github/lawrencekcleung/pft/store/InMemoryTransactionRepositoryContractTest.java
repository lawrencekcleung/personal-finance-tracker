package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs the shared repository contract against the in-memory fixture. */
class InMemoryTransactionRepositoryContractTest extends TransactionRepositoryContractTest {

    private InMemoryTransactionRepository memory;

    @Override
    protected TransactionRepository newRepository() {
        memory = new InMemoryTransactionRepository();
        return memory;
    }

    @Test
    void writeCountTracksMutationsButNotReads() {
        assertEquals(0, memory.writeCount());

        Transaction stored = memory.insert(draft("One"));
        assertEquals(1, memory.writeCount());

        memory.findAll();
        memory.findById(stored.id());
        assertEquals(1, memory.writeCount(), "reads must not count as writes");

        memory.update(new Transaction(stored.id(), stored.date(), stored.type(),
                Money.ofCents(1), "dining", "Edited"));
        assertEquals(2, memory.writeCount());

        memory.deleteById(stored.id());
        assertEquals(3, memory.writeCount());

        memory.insertAll(List.of(draft("Two"), draft("Three")));
        assertEquals(4, memory.writeCount(), "a batch is one write");
    }
}
