package io.github.lawrencekcleung.pft.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The registered rules, kept in explicit order.
 *
 * <p>Categorization rules are ordered because the first match wins. Analysis rules are looked up
 * by id for {@code analyze --rule}. Ids are unique across both lists, so a single id namespace
 * backs {@code --rule} and diagnostics.</p>
 */
public final class RuleRegistry {

    private final List<CategorizationRule> categorizationRules;
    private final List<AnalysisRule> analysisRules;

    private RuleRegistry(List<CategorizationRule> categorizationRules,
                         List<AnalysisRule> analysisRules) {
        this.categorizationRules = categorizationRules;
        this.analysisRules = analysisRules;
    }

    /**
     * Builds the production registry.
     *
     * <p>Rules are registered here as their phases land; registration stays explicit and ordered
     * rather than discoverable by reflection.</p>
     *
     * @return the shipped rules
     */
    public static RuleRegistry defaults() {
        return of(List.of(), List.of());
    }

    /**
     * Builds a registry from explicit lists, validating rule identity.
     *
     * @param categorizationRules ordered categorization rules, first match wins
     * @param analysisRules       analysis rules, looked up by id
     * @return the registry
     * @throws IllegalArgumentException if an id is blank or duplicated, or a description is blank
     */
    public static RuleRegistry of(List<CategorizationRule> categorizationRules,
                                  List<AnalysisRule> analysisRules) {
        List<CategorizationRule> categorization =
                List.copyOf(Objects.requireNonNull(categorizationRules,
                        "categorizationRules is required"));
        List<AnalysisRule> analysis =
                List.copyOf(Objects.requireNonNull(analysisRules, "analysisRules is required"));

        Set<String> ids = new HashSet<>();
        List<Rule> all = new ArrayList<>(categorization);
        all.addAll(analysis);
        for (Rule rule : all) {
            String id = rule.id();
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("rule id is required");
            }
            if (rule.description() == null || rule.description().isBlank()) {
                throw new IllegalArgumentException("rule " + id + " needs a description");
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate rule id: " + id);
            }
        }
        return new RuleRegistry(categorization, analysis);
    }

    /**
     * Returns the categorization rules in application order.
     *
     * @return an immutable list
     */
    public List<CategorizationRule> categorizationRules() {
        return categorizationRules;
    }

    /**
     * Returns the analysis rules in registration order.
     *
     * @return an immutable list
     */
    public List<AnalysisRule> analysisRules() {
        return analysisRules;
    }

    /**
     * Looks up an analysis rule by id.
     *
     * @param id the rule id
     * @return the rule, or empty when no analysis rule has that id
     */
    public Optional<AnalysisRule> analysisRule(String id) {
        return analysisRules.stream()
                .filter(rule -> rule.id().equals(id))
                .findFirst();
    }
}
