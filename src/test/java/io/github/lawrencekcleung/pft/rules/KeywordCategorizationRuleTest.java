package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeywordCategorizationRuleTest {

    private final KeywordCategorizationRule rule = new KeywordCategorizationRule();

    private static Transaction described(String description) {
        return new Transaction(1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE,
                Money.ofCents(100), null, description);
    }

    private static Map<String, List<String>> shippedTable() {
        Map<String, List<String>> table = new LinkedHashMap<>();
        table.put("groceries", List.of("countdown", "woolworths", "new world", "paknsave"));
        table.put("rent", List.of("rent", "landlord"));
        table.put("transport",
                List.of("uber", "mrt", "cityhop", "petrol", "caltex", "z energy"));
        table.put("subscriptions", List.of("netflix", "spotify", "icloud"));
        table.put("dining", List.of("cafe", "coffee", "restaurant", "lunch"));
        table.put("health", List.of("pharmacy", "chemist", "doctor", "dentist"));
        table.put("utilities", List.of("electricity", "water", "internet", "broadband"));
        return table;
    }

    @Test
    void everyShippedKeywordMapsToItsCategory() {
        for (Map.Entry<String, List<String>> entry : shippedTable().entrySet()) {
            for (String keyword : entry.getValue()) {
                assertEquals(Optional.of(entry.getKey()),
                        rule.classify(described("Payment to " + keyword)),
                        () -> "keyword '" + keyword + "' should map to " + entry.getKey());
            }
        }
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertEquals(Optional.of("groceries"), rule.classify(described("COUNTDOWN Auckland")));
        assertEquals(Optional.of("transport"), rule.classify(described("Uber ride")));
    }

    @Test
    void matchingIsASubstringTest() {
        assertEquals(Optional.of("rent"), rule.classify(described("Landlord payment")));
        assertEquals(Optional.of("utilities"),
                rule.classify(described("Home broadband bill")));
    }

    @Test
    void multiWordKeywordsMatch() {
        assertEquals(Optional.of("groceries"), rule.classify(described("New World Victoria")));
        assertEquals(Optional.of("transport"), rule.classify(described("Z Energy Kingsland")));
    }

    @Test
    void firstMatchWins() {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        keywords.put("first", List.of("coffee"));
        keywords.put("second", List.of("coffee", "cafe"));

        KeywordCategorizationRule custom = new KeywordCategorizationRule(keywords);

        assertEquals(Optional.of("first"), custom.classify(described("Coffee cart")));
    }

    @Test
    void noMatchReturnsEmpty() {
        assertEquals(Optional.empty(), rule.classify(described("Mystery shop")));
    }

    @Test
    void identityIsStable() {
        assertEquals("keyword", rule.id());
        assertEquals("assigns a category by matching description keywords", rule.description());
    }

    @Test
    void rejectsAnEmptyTableOrBlankKeywords() {
        assertThrows(IllegalArgumentException.class,
                () -> new KeywordCategorizationRule(Map.of()));

        Map<String, List<String>> blank = new LinkedHashMap<>();
        blank.put("category", List.of(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new KeywordCategorizationRule(blank));
    }
}
