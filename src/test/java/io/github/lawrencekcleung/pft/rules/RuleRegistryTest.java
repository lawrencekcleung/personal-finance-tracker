package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleRegistryTest {

    private static CategorizationRule categorization(String id) {
        return new CategorizationRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String description() {
                return "categorizes with " + id;
            }

            @Override
            public Optional<String> classify(Transaction tx) {
                return Optional.empty();
            }
        };
    }

    private static AnalysisRule analysis(String id) {
        return new AnalysisRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String description() {
                return "analyses with " + id;
            }

            @Override
            public List<Finding> evaluate(AnalysisContext ctx) {
                return List.of();
            }
        };
    }

    @Test
    void keepsRegistrationOrder() {
        RuleRegistry registry = RuleRegistry.of(
                List.of(categorization("first"), categorization("second")),
                List.of(analysis("one"), analysis("two")));

        assertEquals(List.of("first", "second"),
                registry.categorizationRules().stream().map(Rule::id).toList());
        assertEquals(List.of("one", "two"),
                registry.analysisRules().stream().map(Rule::id).toList());
    }

    @Test
    void looksUpAnAnalysisRuleAndReportsMisses() {
        RuleRegistry registry = RuleRegistry.of(List.of(), List.of(analysis("duplicate-charge")));

        assertEquals("duplicate-charge", registry.analysisRule("duplicate-charge").orElseThrow().id());
        assertTrue(registry.analysisRule("missing").isEmpty());
    }

    @Test
    void duplicateIdsWithinAListAreRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RuleRegistry.of(List.of(categorization("same"), categorization("same")),
                        List.of()));

        assertEquals("duplicate rule id: same", e.getMessage());
    }

    @Test
    void duplicateIdsAcrossListsAreRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RuleRegistry.of(List.of(categorization("same")),
                        List.of(analysis("same"))));

        assertEquals("duplicate rule id: same", e.getMessage());
    }

    @Test
    void blankIdIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RuleRegistry.of(List.of(), List.of(analysis("  "))));

        assertEquals("rule id is required", e.getMessage());
    }

    @Test
    void blankDescriptionIsRejected() {
        AnalysisRule noDescription = new AnalysisRule() {
            @Override
            public String id() {
                return "quiet";
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public List<Finding> evaluate(AnalysisContext ctx) {
                return List.of();
            }
        };

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RuleRegistry.of(List.of(), List.of(noDescription)));

        assertEquals("rule quiet needs a description", e.getMessage());
    }

    @Test
    void defaultsIsAvailable() {
        RuleRegistry registry = RuleRegistry.defaults();

        assertEquals(registry.categorizationRules().size(),
                registry.categorizationRules().stream().map(Rule::id).distinct().count());
        assertEquals(registry.analysisRules().size(),
                registry.analysisRules().stream().map(Rule::id).distinct().count());
    }
}
