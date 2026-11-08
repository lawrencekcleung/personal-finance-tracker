package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvInsertAllTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("data/transactions.csv");
    }

    private Path counter() {
        return dir.resolve("data/transactions.csv.seq");
    }

    private CsvTransactionRepository repository() {
        return new CsvTransactionRepository(file());
    }

    private static Transaction draft(String description) {
        return new Transaction(0, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE,
                Money.ofCents(4250), "groceries", description);
    }

    @Test
    void insertsTheWholeBatchAndWritesTheCounterOnce() throws IOException {
        List<Transaction> stored = repository().insertAll(List.of(draft("One"), draft("Two")));

        assertEquals(List.of(1, 2), stored.stream().map(Transaction::id).toList());
        assertEquals("3", Files.readString(counter(), StandardCharsets.UTF_8).trim());
        assertEquals(CsvCodec.HEADER + "\n"
                        + "1,2026-11-01,expense,42.50,groceries,One\n"
                        + "2,2026-11-01,expense,42.50,groceries,Two\n",
                Files.readString(file(), StandardCharsets.UTF_8));
    }

    @Test
    void aFailedBatchPersistsNothing() throws IOException {
        CsvTransactionRepository repo = repository();
        repo.insert(draft("Existing"));
        String dataBefore = Files.readString(file(), StandardCharsets.UTF_8);
        String counterBefore = Files.readString(counter(), StandardCharsets.UTF_8);

        assertThrows(NullPointerException.class,
                () -> repo.insertAll(Arrays.asList(draft("Two"), null)));

        assertEquals(dataBefore, Files.readString(file(), StandardCharsets.UTF_8));
        assertEquals(counterBefore, Files.readString(counter(), StandardCharsets.UTF_8));
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void anEmptyBatchWritesNothing() {
        assertTrue(repository().insertAll(List.of()).isEmpty());
        assertTrue(Files.notExists(file()));
        assertTrue(Files.notExists(counter()));
    }

    @Test
    void aBatchAfterADeleteContinuesFromTheHighWaterMark() {
        CsvTransactionRepository repo = repository();
        repo.insert(draft("One"));
        repo.insert(draft("Two"));
        repo.deleteById(2);

        List<Transaction> stored = repo.insertAll(List.of(draft("Three"), draft("Four")));

        assertEquals(List.of(3, 4), stored.stream().map(Transaction::id).toList());
    }
}
