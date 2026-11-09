package io.github.lawrencekcleung.pft.config;

import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.model.Money;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads the optional {@code pft.properties} file.
 *
 * <p>The file is deliberately tiny: Java properties syntax, comments allowed, unknown keys
 * ignored. Only {@code monthly.budget} is understood today; the loader makes adding another
 * setting a one-line change.</p>
 */
public final class ConfigLoader {

    /** The default config file, relative to the current directory. */
    public static final String FILE_NAME = "pft.properties";

    /** The key for the monthly expense budget. */
    public static final String MONTHLY_BUDGET_KEY = "monthly.budget";

    private ConfigLoader() {
    }

    /**
     * Loads the config if it exists, otherwise returns the built-in defaults.
     *
     * @param file the config file
     * @return the loaded or default configuration
     */
    public static Budget loadOrDefault(Path file) {
        return Files.exists(file) ? load(file) : Budget.defaults();
    }

    /**
     * Loads an explicitly requested config file.
     *
     * @param file the config file
     * @return the loaded configuration
     * @throws DataException    if the file is missing or a value is invalid
     * @throws StorageException if the file exists but cannot be read
     */
    public static Budget load(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (NoSuchFileException e) {
            throw new DataException("config file not found: " + file);
        } catch (IOException e) {
            throw new StorageException("cannot read " + file + ": " + e.getMessage(), e);
        }

        String raw = properties.getProperty(MONTHLY_BUDGET_KEY);
        if (raw == null || raw.isBlank()) {
            return Budget.defaults();
        }
        String value = raw.trim();
        try {
            return new Budget(Money.parse(value));
        } catch (IllegalArgumentException e) {
            throw new DataException(file + ": invalid " + MONTHLY_BUDGET_KEY
                    + " '" + value + "': " + e.getMessage());
        }
    }
}
