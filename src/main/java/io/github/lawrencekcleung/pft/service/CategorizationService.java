package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.model.Category;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.rules.CategorizationRule;
import io.github.lawrencekcleung.pft.rules.RuleRegistry;
import io.github.lawrencekcleung.pft.store.TransactionRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Plans and applies category assignments.
 *
 * <p>Keyword rules only ever touch {@link Category#UNCATEGORIZED} rows, and a manual override is
 * final: once a row has a category, later plan/apply runs leave it alone. Planning is pure, so a
 * dry run cannot write.</p>
 */
public final class CategorizationService {

    private final TransactionRepository repository;
    private final RuleRegistry rules;

    /**
     * Creates the service.
     *
     * @param repository the store to read and update
     * @param rules      the rules to consult, in order
     */
    public CategorizationService(TransactionRepository repository, RuleRegistry rules) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.rules = Objects.requireNonNull(rules, "rules is required");
    }

    /**
     * Computes the keyword-rule changes without writing anything.
     *
     * <p>Only uncategorized transactions are considered, in id order, and the first matching rule
     * wins. A transaction no rule recognises produces no change.</p>
     *
     * @return an immutable list of proposed changes
     */
    public List<CategoryChange> plan() {
        List<CategoryChange> changes = new ArrayList<>();
        repository.findAll().stream()
                .filter(tx -> Category.UNCATEGORIZED.equals(tx.category()))
                .sorted(Comparator.comparingInt(Transaction::id))
                .forEach(tx -> classify(tx).ifPresent(to ->
                        changes.add(new CategoryChange(tx, tx.category(), to))));
        return List.copyOf(changes);
    }

    /**
     * Applies previously planned changes one repository update at a time.
     *
     * @param changes the plan to apply
     * @return how many transactions were updated
     */
    public int apply(List<CategoryChange> changes) {
        int applied = 0;
        for (CategoryChange change : changes) {
            repository.update(withCategory(change.transaction(), change.to()));
            applied++;
        }
        return applied;
    }

    /**
     * Assigns a category manually, bypassing the rules.
     *
     * @param id       the transaction id
     * @param category the new category
     * @return the updated transaction
     * @throws DataException if the category is blank or the id is unknown
     */
    public Transaction override(int id, String category) {
        if (category == null || category.isBlank()) {
            throw new DataException("category is required");
        }
        Transaction current = repository.findById(id).orElseThrow(
                () -> new DataException("unknown transaction id: " + id));
        Transaction updated = withCategory(current, category);
        repository.update(updated);
        return updated;
    }

    private Optional<String> classify(Transaction tx) {
        for (CategorizationRule rule : rules.categorizationRules()) {
            Optional<String> category = rule.classify(tx);
            if (category.isPresent()) {
                return category;
            }
        }
        return Optional.empty();
    }

    private static Transaction withCategory(Transaction tx, String category) {
        return new Transaction(tx.id(), tx.date(), tx.type(), tx.amount(), category,
                tx.description());
    }
}
