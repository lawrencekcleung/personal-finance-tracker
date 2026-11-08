package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Transaction;

import java.util.List;

/**
 * What one import file produced: the valid drafts and the rows that were skipped.
 *
 * @param drafts the valid transactions, with id {@code 0} until the store assigns one
 * @param errors the skipped rows, in source order
 */
public record ParsedImport(List<Transaction> drafts, List<ImportError> errors) {

    /**
     * Freezes both lists.
     */
    public ParsedImport {
        drafts = List.copyOf(drafts);
        errors = List.copyOf(errors);
    }
}
