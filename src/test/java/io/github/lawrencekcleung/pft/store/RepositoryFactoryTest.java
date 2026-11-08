package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.error.UsageException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryFactoryTest {

    @Test
    void csvExtensionOpensTheCsvAdapter() {
        assertInstanceOf(CsvTransactionRepository.class,
                RepositoryFactory.open(Path.of("data/transactions.csv")));
    }

    @Test
    void uppercaseCsvExtensionIsAccepted() {
        assertInstanceOf(CsvTransactionRepository.class,
                RepositoryFactory.open(Path.of("data/TRANSACTIONS.CSV")));
    }

    @Test
    void sqliteIsRejectedUntilTheAdapterExists() {
        UsageException e = assertThrows(UsageException.class,
                () -> RepositoryFactory.open(Path.of("data/pft.db")));

        assertEquals("unsupported data file 'data/pft.db': expected a .csv file",
                e.getMessage());
    }
}
