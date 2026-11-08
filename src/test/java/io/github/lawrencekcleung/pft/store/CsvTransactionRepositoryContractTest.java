package io.github.lawrencekcleung.pft.store;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/** Runs the shared repository contract against the CSV adapter. */
class CsvTransactionRepositoryContractTest extends TransactionRepositoryContractTest {

    @TempDir
    Path dir;

    @Override
    protected TransactionRepository newRepository() {
        return new CsvTransactionRepository(dir.resolve("data/transactions.csv"));
    }
}
