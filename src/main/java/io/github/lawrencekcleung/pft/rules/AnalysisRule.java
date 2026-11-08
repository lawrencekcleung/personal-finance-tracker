package io.github.lawrencekcleung.pft.rules;

import java.util.List;

/**
 * A rule that inspects a set of transactions and reports findings.
 *
 * <p>Evaluation is pure and read-only: the same context must always produce the same list, in the
 * same order. Findings reference real transaction ids from the context.</p>
 */
public non-sealed interface AnalysisRule extends Rule {

    /**
     * Evaluates the rule.
     *
     * @param ctx the transactions to inspect
     * @return the findings, deterministically ordered
     */
    List<Finding> evaluate(AnalysisContext ctx);
}
