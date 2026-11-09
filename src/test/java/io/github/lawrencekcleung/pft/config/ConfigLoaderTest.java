package io.github.lawrencekcleung.pft.config;

import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.model.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path dir;

    private Path config(String content) throws IOException {
        Path file = dir.resolve("pft.properties");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void aMissingDefaultFileUsesTheBuiltInBudget() {
        Budget budget = ConfigLoader.loadOrDefault(dir.resolve("absent.properties"));

        assertEquals(Money.ofCents(50_000), budget.monthlyLimit());
    }

    @Test
    void readsTheMonthlyBudget() throws IOException {
        Budget budget = ConfigLoader.load(config("monthly.budget=750.50\n"));

        assertEquals(Money.ofCents(75_050), budget.monthlyLimit());
    }

    @Test
    void ignoresCommentsAndUnknownKeys() throws IOException {
        Budget budget = ConfigLoader.load(config("""
                # a comment
                other.setting = ignored
                monthly.budget = 12.34
                """));

        assertEquals(Money.ofCents(1_234), budget.monthlyLimit());
    }

    @Test
    void aBlankBudgetUsesTheDefault() throws IOException {
        Budget budget = ConfigLoader.load(config("monthly.budget=\n"));

        assertEquals(Budget.DEFAULT_MONTHLY_LIMIT, budget.monthlyLimit());
    }

    @Test
    void anInvalidBudgetIsADataException() throws IOException {
        Path file = config("monthly.budget=abc\n");

        DataException e = assertThrows(DataException.class, () -> ConfigLoader.load(file));

        assertTrue(e.getMessage().startsWith(file + ": invalid monthly.budget 'abc'"));
    }

    @Test
    void anExplicitlyMissingFileIsADataException() {
        Path file = dir.resolve("missing.properties");

        DataException e = assertThrows(DataException.class, () -> ConfigLoader.load(file));

        assertEquals("config file not found: " + file, e.getMessage());
    }
}
