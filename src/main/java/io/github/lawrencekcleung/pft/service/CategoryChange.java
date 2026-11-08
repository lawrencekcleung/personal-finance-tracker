package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Transaction;

/**
 * One proposed category change produced by the categorization rules.
 *
 * @param transaction the transaction as it currently stands
 * @param from        its current category
 * @param to          the category the rules propose
 */
public record CategoryChange(Transaction transaction, String from, String to) {
}
