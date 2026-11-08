package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import io.github.lawrencekcleung.pft.store.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportServiceTest {

    private InMemoryTransactionRepository repository;
    private ImportService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        service = new ImportService(repository);
    }

    private void seed(int count) {
        for (int i = 0; i < count; i++) {
            repository.insert(new Transaction(0, LocalDate.of(2026, 10, 1),
                    TransactionType.EXPENSE, Money.ofCents(100), "groceries", "Seed"));
        }
    }

    @Test
    void importsValidRowsInASingleWrite() {
        ImportReport report = service.importCsv("""
                date,type,amount,description
                2026-11-01,expense,42.50,One
                2026-11-02,income,5000.00,Two
                """);

        assertEquals(2, report.imported());
        assertFalse(report.hasErrors());
        assertEquals(1, repository.writeCount(), "the batch is one write");
        assertEquals(List.of(1, 2),
                repository.findAll().stream().map(Transaction::id).toList());
    }

    @Test
    void skippedRowsConsumeNoId() {
        seed(2);
        int writesBefore = repository.writeCount();

        ImportReport report = service.importCsv("""
                date,type,amount,description
                2026-11-01,expense,1.00,Good one
                2026-11-02,expense,abc,Broken
                2026-11-03,expense,2.00,Good two
                """);

        assertEquals(2, report.imported());
        assertEquals(1, report.skipped());
        assertEquals(List.of(1, 2, 3, 4),
                repository.findAll().stream().map(Transaction::id).toList());
        assertEquals(writesBefore + 1, repository.writeCount(),
                "only the imported batch was written");
    }

    @Test
    void allRowsBadWritesNothing() {
        ImportReport report = service.importCsv("""
                2026-13-01,expense,1.00,Bad date
                2026-11-01,transfer,1.00,Bad type
                """);

        assertEquals(0, report.imported());
        assertEquals(2, report.skipped());
        assertEquals(0, repository.writeCount());
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void emptyFileWritesNothing() {
        ImportReport report = service.importCsv("");

        assertEquals(0, report.imported());
        assertFalse(report.hasErrors());
        assertEquals(0, repository.writeCount());
    }

    @Test
    void reportKeepsSourceLineNumbers() {
        ImportReport report = service.importCsv("""
                date,type,amount,description
                2026-11-01,expense,1.00,One
                bad-date,expense,1.00,Two
                """);

        assertEquals(1, report.skipped());
        assertEquals(3, report.errors().get(0).lineNumber());
    }
}
