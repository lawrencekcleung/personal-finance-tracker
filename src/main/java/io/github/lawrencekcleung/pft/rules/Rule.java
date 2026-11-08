package io.github.lawrencekcleung.pft.rules;

/**
 * Something the tracker knows how to do to transactions: assign a category or report a finding.
 *
 * <p>Rules are registered explicitly in {@link RuleRegistry} and identified by a stable, unique
 * id. There is no reflection, service loader or config file, so registration order and identity
 * are reviewable in one place.</p>
 */
public sealed interface Rule permits CategorizationRule, AnalysisRule {

    /**
     * Returns the stable id used by {@code --rule} and in output.
     *
     * @return a non-blank, registry-unique id
     */
    String id();

    /**
     * Returns a one-line description of what the rule does.
     *
     * @return a non-blank description
     */
    String description();
}
