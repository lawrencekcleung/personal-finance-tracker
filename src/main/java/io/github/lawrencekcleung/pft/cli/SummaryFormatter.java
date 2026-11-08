package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.service.CategorySpend;
import io.github.lawrencekcleung.pft.service.Summary;

import java.util.Locale;

/**
 * Renders a {@link Summary} as the PRD's aligned block.
 *
 * <p>Layout is fixed: 24 characters for the category, then 10 each for spend and share. Net is
 * signed; percentages arrive already formatted by {@link CategorySpend#share()}.</p>
 */
public final class SummaryFormatter {

    private static final String ROW_FORMAT = "%-24s%10s%10s";

    /**
     * Renders the summary.
     *
     * @param summary the summary to render
     * @return the block, without a trailing newline
     */
    public String format(Summary summary) {
        if (summary.isEmpty()) {
            return "No transactions.";
        }

        StringBuilder text = new StringBuilder()
                .append("Period: ").append(periodLabel(summary))
                .append("   Income: ").append(summary.income().format())
                .append("   Expenses: ").append(summary.expenses().format())
                .append("   Net: ").append(summary.net().formatSigned());

        if (!summary.categories().isEmpty()) {
            text.append("\n\n")
                    .append(String.format(Locale.ROOT, ROW_FORMAT, "Category", "Spend", "Share"));
            for (CategorySpend category : summary.categories()) {
                text.append('\n').append(String.format(Locale.ROOT, ROW_FORMAT,
                        category.category(), category.spend().format(), category.share()));
            }
        }
        return text.toString();
    }

    private static String periodLabel(Summary summary) {
        return summary.period() == null ? "all time" : summary.period().toString();
    }
}
