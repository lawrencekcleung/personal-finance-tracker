package io.github.lawrencekcleung.pft.rules;

import java.util.List;

/**
 * One thing an {@link AnalysisRule} found, referencing the transactions involved.
 *
 * @param ruleId         the id of the rule that produced the finding
 * @param transactionIds the ids of the transactions the finding is about
 * @param message        the human-readable detail, without any prefix
 */
public record Finding(String ruleId, List<Integer> transactionIds, String message) {

    /**
     * Validates and freezes the finding.
     *
     * @throws IllegalArgumentException if the rule id or message is blank
     */
    public Finding {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("rule id is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("finding message is required");
        }
        transactionIds = List.copyOf(transactionIds);
    }
}
