package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.rules.Finding;

import java.util.stream.Collectors;

/**
 * Formats an analysis finding.
 *
 * <p>This is the single place findings become text, so every rule — present and future — renders
 * identically: {@code [rule-id] #id, #id  message}. The message itself is owned by the rule.</p>
 */
public final class FindingPrinter {

    /**
     * Renders one finding.
     *
     * @param finding the finding to render
     * @return the line, without a trailing newline
     */
    public String format(Finding finding) {
        StringBuilder text = new StringBuilder()
                .append('[').append(finding.ruleId()).append("] ");
        if (!finding.transactionIds().isEmpty()) {
            text.append(finding.transactionIds().stream()
                            .map(id -> "#" + id)
                            .collect(Collectors.joining(", ")))
                    .append("  ");
        }
        return text.append(finding.message()).toString();
    }
}
