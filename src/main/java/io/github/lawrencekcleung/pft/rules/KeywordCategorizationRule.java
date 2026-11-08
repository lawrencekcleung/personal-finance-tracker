package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Transaction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Assigns a category by matching keywords in the transaction description.
 *
 * <p>The match is a case-insensitive substring test. Categories are checked in declaration order
 * and the first match wins, so the map's ordering is part of the rule. The shipped table is the
 * one documented in the PRD; tests can inject a smaller ordered map to exercise precedence.</p>
 */
public final class KeywordCategorizationRule implements CategorizationRule {

    /** The shipped keyword table, in first-match order. */
    private static final Map<String, List<String>> DEFAULT_KEYWORDS = defaultKeywords();

    private final Map<String, List<String>> keywords;

    /**
     * Creates the shipped rule.
     */
    public KeywordCategorizationRule() {
        this(DEFAULT_KEYWORDS);
    }

    /**
     * Creates a rule with an explicit ordered keyword table.
     *
     * @param keywords category to keywords, in first-match order
     * @throws IllegalArgumentException if no category is declared or a keyword is blank
     */
    public KeywordCategorizationRule(Map<String, List<String>> keywords) {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        keywords.forEach((category, words) -> {
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("keyword category is required");
            }
            for (String word : words) {
                if (word == null || word.isBlank()) {
                    throw new IllegalArgumentException(
                            "keyword for " + category + " must not be blank");
                }
            }
            copy.put(category, List.copyOf(words));
        });
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("at least one keyword category is required");
        }
        this.keywords = Collections.unmodifiableMap(copy);
    }

    @Override
    public String id() {
        return "keyword";
    }

    @Override
    public String description() {
        return "assigns a category by matching description keywords";
    }

    @Override
    public Optional<String> classify(Transaction tx) {
        String description = tx.description().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : keywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (description.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    private static Map<String, List<String>> defaultKeywords() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("groceries", List.of("countdown", "woolworths", "new world", "paknsave"));
        map.put("rent", List.of("rent", "landlord"));
        map.put("transport", List.of("uber", "mrt", "cityhop", "petrol", "caltex", "z energy"));
        map.put("subscriptions", List.of("netflix", "spotify", "icloud"));
        map.put("dining", List.of("cafe", "coffee", "restaurant", "lunch"));
        map.put("health", List.of("pharmacy", "chemist", "doctor", "dentist"));
        map.put("utilities", List.of("electricity", "water", "internet", "broadband"));
        return Collections.unmodifiableMap(map);
    }
}
