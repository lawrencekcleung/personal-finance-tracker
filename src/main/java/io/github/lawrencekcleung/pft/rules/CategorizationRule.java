package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Transaction;

import java.util.Optional;

/**
 * A rule that proposes a category for a transaction.
 *
 * <p>The categorization pipeline applies the rules in registration order and stops at the first
 * match, so the order is part of the definition. A rule only proposes a category; the service
 * decides whether the transaction may change.</p>
 */
public non-sealed interface CategorizationRule extends Rule {

    /**
     * Proposes a category for the transaction.
     *
     * @param tx the transaction to classify
     * @return the proposed category, or empty when the rule does not match
     */
    Optional<String> classify(Transaction tx);
}
