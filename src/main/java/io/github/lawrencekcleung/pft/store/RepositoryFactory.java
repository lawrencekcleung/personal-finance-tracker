package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.error.UsageException;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Opens the storage adapter named by the data file's extension.
 *
 * <p>This is the only class that knows which formats exist, so swapping stores is configuration,
 * not code: {@code --data data/transactions.csv} works today, and
 * {@code --data data/pft.db} will start working the day {@code SqliteTransactionRepository}
 * lands.</p>
 */
public final class RepositoryFactory {

    private RepositoryFactory() {
    }

    /**
     * Opens a repository for the given data file. The file need not exist yet.
     *
     * @param dataPath path to the data file; its extension selects the adapter
     * @return a repository over that file
     * @throws UsageException if no adapter handles the extension
     */
    public static TransactionRepository open(Path dataPath) {
        Objects.requireNonNull(dataPath, "dataPath is required");
        Path fileName = dataPath.getFileName();
        String name = fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return new CsvTransactionRepository(dataPath);
        }
        throw new UsageException(
                "unsupported data file '" + dataPath + "': expected a .csv file");
    }
}
