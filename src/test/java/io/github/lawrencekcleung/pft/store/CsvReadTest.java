package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.error.StorageException;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvReadTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("data/transactions.csv");
    }

    private CsvTransactionRepository repository() {
        return new CsvTransactionRepository(file());
    }

    private void write(String content) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), content, StandardCharsets.UTF_8);
    }

    @Test
    void missingFileIsAnEmptyStore() {
        assertTrue(repository().findAll().isEmpty());
        assertTrue(repository().findById(1).isEmpty());
        assertTrue(Files.notExists(file()), "reading must not create the file");
        assertTrue(Files.notExists(file().getParent()), "reading must not create directories");
    }

    @Test
    void headerOnlyFileIsEmpty() throws IOException {
        write(CsvCodec.HEADER + "\n");

        assertTrue(repository().findAll().isEmpty());
    }

    @Test
    void zeroByteFileIsEmpty() throws IOException {
        write("");

        assertTrue(repository().findAll().isEmpty());
    }

    @Test
    void readsTransactionsInFileOrder() throws IOException {
        write(CsvCodec.HEADER + "\n"
                + "1,2026-11-01,income,5000.00,income,Salary Oct\n"
                + "2,2026-11-01,expense,42.50,groceries,\"Countdown, Auckland\"\n");

        List<Transaction> transactions = repository().findAll();

        assertEquals(2, transactions.size());
        assertEquals(1, transactions.get(0).id());
        assertEquals(LocalDate.of(2026, 11, 1), transactions.get(0).date());
        assertEquals(TransactionType.INCOME, transactions.get(0).type());
        assertEquals(Money.ofCents(500000), transactions.get(0).amount());
        assertEquals("Countdown, Auckland", transactions.get(1).description());
    }

    @Test
    void findByIdReturnsTheMatchAndEmptyForAMiss() throws IOException {
        write(CsvCodec.HEADER + "\n"
                + "1,2026-11-01,expense,42.50,groceries,One\n"
                + "2,2026-11-02,expense,10.00,dining,Two\n");

        assertEquals("One", repository().findById(1).orElseThrow().description());
        assertTrue(repository().findById(99).isEmpty());
    }

    @Test
    void corruptRecordReportsPathAndLineNumber() throws IOException {
        write(CsvCodec.HEADER + "\n"
                + "1,2026-11-01,expense,42.50,groceries\n");

        StorageException e = assertThrows(StorageException.class, () -> repository().findAll());

        assertEquals(file() + ":2: expected 6 columns, got 5", e.getMessage());
    }

    @Test
    void duplicateIdIsRejected() throws IOException {
        write(CsvCodec.HEADER + "\n"
                + "1,2026-11-01,expense,42.50,groceries,One\n"
                + "1,2026-11-02,expense,10.00,dining,Two\n");

        StorageException e = assertThrows(StorageException.class, () -> repository().findAll());

        assertTrue(e.getMessage().endsWith(":3: duplicate id 1"));
    }
}
