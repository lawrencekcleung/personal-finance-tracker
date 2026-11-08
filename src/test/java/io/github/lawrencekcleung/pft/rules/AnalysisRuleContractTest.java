package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract every {@link AnalysisRule} must satisfy, regardless of what it looks for.
 *
 * <p>One subclass supplies the rule and a representative context. Rules add their own behaviour
 * tests on top; this base guards identity, determinism and referential integrity so a new rule
 * cannot quietly break the framework's promises.</p>
 */
abstract class AnalysisRuleContractTest {

    /** Returns the rule under test. */
    protected abstract AnalysisRule rule();

    /** Returns a context containing at least one expense and one income. */
    protected abstract AnalysisContext context();

    @Test
    void identityIsStableAndDescribed() {
        AnalysisRule rule = rule();

        assertFalse(rule.id() == null || rule.id().isBlank(), "rule id is required");
        assertFalse(rule.description() == null || rule.description().isBlank(),
                "rule description is required");
    }

    @Test
    void evaluationIsDeterministic() {
        assertEquals(rule().evaluate(context()), rule().evaluate(context()));
    }

    @Test
    void findingsCarryTheRuleId() {
        for (Finding finding : rule().evaluate(context())) {
            assertEquals(rule().id(), finding.ruleId());
        }
    }

    @Test
    void findingsReferenceTransactionsFromTheContext() {
        Map<Integer, Transaction> byId = context().transactions().stream()
                .collect(Collectors.toMap(Transaction::id, Function.identity()));

        for (Finding finding : rule().evaluate(context())) {
            for (int id : finding.transactionIds()) {
                assertTrue(byId.containsKey(id), () -> "unknown transaction id: " + id);
            }
        }
    }

    @Test
    void findingsOnlyReferenceExpenses() {
        Map<Integer, Transaction> byId = context().transactions().stream()
                .collect(Collectors.toMap(Transaction::id, Function.identity()));

        for (Finding finding : rule().evaluate(context())) {
            for (int id : finding.transactionIds()) {
                assertTrue(byId.get(id).isExpense(),
                        () -> "finding references a non-expense transaction: " + id);
            }
        }
    }

    @Test
    void findingsHaveMessages() {
        for (Finding finding : rule().evaluate(context())) {
            assertFalse(finding.message().isBlank(), "finding message is required");
        }
    }

    @Test
    void evaluationDoesNotMutateTheContext() {
        AnalysisContext ctx = context();
        List<Transaction> before = List.copyOf(ctx.transactions());

        rule().evaluate(ctx);

        assertEquals(before, ctx.transactions());
    }
}
