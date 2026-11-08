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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvWriteTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("nested/data/transactions.csv");
    }

    private CsvTransactionRepository repository() {
        return new CsvTransactionRepository(file());
    }

    private static Transaction draft(String description) {
        return new Transaction(0, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE,
                Money.ofCents(4250), "groceries", description);
    }

    @Test
    void insertCreatesParentDirectoriesAndHeader() throws IOException {
        repository().insert(draft("Countdown"));

        assertTrue(Files.exists(file()));
        assertEquals(CsvCodec.HEADER + "\n"
                + "1,2026-11-01,expense,42.50,groceries,Countdown\n",
                Files.readString(file(), StandardCharsets.UTF_8));
    }

    @Test
    void insertReturnsTheStoredTransactionWithItsId() {
        Transaction stored = repository().insert(draft("Countdown"));

        assertEquals(1, stored.id());
        assertEquals("Countdown", stored.description());
        assertEquals(Money.ofCents(4250), stored.amount());
    }

    @Test
    void insertIgnoresTheDraftId() {
        Transaction withId = new Transaction(99, LocalDate.of(2026, 11, 1),
                TransactionType.EXPENSE, Money.ofCents(100), "groceries", "Shop");

        assertEquals(1, repository().insert(withId).id());
    }

    @Test
    void insertsAssignIncreasingIds() {
        CsvTransactionRepository repo = repository();

        assertEquals(1, repo.insert(draft("One")).id());
        assertEquals(2, repo.insert(draft("Two")).id());
        assertEquals(3, repo.insert(draft("Three")).id());
        assertEquals(3, repo.findAll().size());
    }

    @Test
    void updateReplacesInPlaceAndPreservesOrder() {
        CsvTransactionRepository repo = repository();
        Transaction first = repo.insert(draft("One"));
        repo.insert(draft("Two"));

        Transaction updated = new Transaction(first.id(), first.date(), first.type(),
                Money.ofCents(9999), "dining", "One edited");
        repo.update(updated);

        List<Transaction> all = repo.findAll();
        assertEquals(2, all.size());
        assertEquals(first.id(), all.get(0).id());
        assertEquals(updated, all.get(0));
        assertEquals("Two", all.get(1).description());
    }

    @Test
    void updateWithUnknownIdThrowsAndLeavesFileUnchanged() throws IOException {
        CsvTransactionRepository repo = repository();
        repo.insert(draft("One"));
        String before = Files.readString(file(), StandardCharsets.UTF_8);

        Transaction ghost = new Transaction(42, LocalDate.of(2026, 11, 2),
                TransactionType.EXPENSE, Money.ofCents(1), "groceries", "Ghost");

        StorageException e = assertThrows(StorageException.class, () -> repo.update(ghost));

        assertEquals("unknown transaction id: 42", e.getMessage());
        assertEquals(before, Files.readString(file(), StandardCharsets.UTF_8));
    }

    @Test
    void deleteRemovesTheRow() {
        CsvTransactionRepository repo = repository();
        repo.insert(draft("One"));
        repo.insert(draft("Two"));

        repo.deleteById(1);

        List<Transaction> all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals(2, all.get(0).id());
    }

    @Test
    void deleteWithUnknownIdThrowsAndLeavesFileUnchanged() throws IOException {
        CsvTransactionRepository repo = repository();
        repo.insert(draft("One"));
        String before = Files.readString(file(), StandardCharsets.UTF_8);

        StorageException e = assertThrows(StorageException.class, () -> repo.deleteById(7));

        assertEquals("unknown transaction id: 7", e.getMessage());
        assertEquals(before, Files.readString(file(), StandardCharsets.UTF_8));
    }

    @Test
    void deletedIdsAreNeverReused() {
        CsvTransactionRepository repo = repository();
        repo.insert(draft("One"));
        repo.insert(draft("Two"));
        repo.deleteById(2);

        assertEquals(3, repo.insert(draft("Three")).id());
    }

    @Test
    void insertRecordsTheNextIdInTheSidecarFile() throws IOException {
        repository().insert(draft("One"));

        Path counter = file().resolveSibling("transactions.csv.seq");
        assertEquals("2", Files.readString(counter, StandardCharsets.UTF_8).trim());
    }

    @Test
    void deletingTheSidecarFallsBackToTheHighestIdInTheDataFile() throws IOException {
        CsvTransactionRepository repo = repository();
        repo.insert(draft("One"));
        repo.insert(draft("Two"));
        Files.delete(file().resolveSibling("transactions.csv.seq"));

        assertEquals(3, repo.insert(draft("Three")).id());
        assertEquals("4", Files.readString(file().resolveSibling("transactions.csv.seq"),
                StandardCharsets.UTF_8).trim());
    }

    @Test
    void sidecarSurvivesDeletingTheLastRowSoItsIdIsNotReused() throws IOException {
        CsvTransactionRepository repo = repository();
        repo.insert(draft("One"));
        repo.insert(draft("Two"));
        repo.deleteById(2);

        assertEquals("3", Files.readString(file().resolveSibling("transactions.csv.seq"),
                StandardCharsets.UTF_8).trim());
        assertEquals(3, repo.insert(draft("Three")).id());
    }

    @Test
    void corruptSidecarIsAStorageExceptionNamingTheFile() throws IOException {
        CsvTransactionRepository repo = repository();
        repo.insert(draft("One"));
        Path counter = file().resolveSibling("transactions.csv.seq");
        Files.writeString(counter, "not-a-number\n", StandardCharsets.UTF_8);

        StorageException e = assertThrows(StorageException.class, () -> repo.insert(draft("Two")));

        assertEquals(counter + ": invalid counter 'not-a-number'", e.getMessage());
        assertEquals(1, repo.findAll().size(), "the failed insert must not change the data file");
    }

    @Test
    void roundTripsCommaQuoteAndUnicodeThroughDisk() {
        CsvTransactionRepository repo = repository();
        Transaction stored = repo.insert(new Transaction(0, LocalDate.of(2026, 11, 3),
                TransactionType.EXPENSE, Money.ofCents(123456), "eating, out",
                "Café, \"the best\"\nand cheapest"));

        assertEquals(stored, repo.findById(stored.id()).orElseThrow());
    }

    @Test
    void writesAmountsWithTwoDecimals() throws IOException {
        repository().insert(new Transaction(0, LocalDate.of(2026, 11, 1),
                TransactionType.INCOME, Money.ofCents(500000000), "income", "Salary"));

        assertTrue(Files.readString(file(), StandardCharsets.UTF_8)
                .contains(",5000000.00,"));
    }

    @Test
    void leavesNoTemporaryFileBehind() {
        repository().insert(draft("One"));

        assertFalse(Files.exists(file().resolveSibling("transactions.csv.tmp")));
        assertFalse(Files.exists(file().resolveSibling("transactions.csv.seq.tmp")));
    }

    @Test
    void closeIsANoOp() {
        repository().close();
    }
}
