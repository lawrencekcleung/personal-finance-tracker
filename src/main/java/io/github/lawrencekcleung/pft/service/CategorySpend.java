package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Money;

/**
 * One category's share of the period's expenses.
 *
 * @param category    the category name
 * @param spend       the total spent in that category
 * @param basisPoints the share of total expenses in basis points (1 bp = 0.01%)
 */
public record CategorySpend(String category, Money spend, long basisPoints) {

    /**
     * Returns the share as a percentage with one decimal, rounded at the display edge.
     *
     * <p>Rounding is done in integers ({@code (basisPoints + 5) / 10} tenths of a percent), so the
     * rendered value never depends on floating point. Rows may not sum to exactly {@code 100.0%}.</p>
     *
     * @return the share, for example {@code "60.1%"}
     */
    public String share() {
        long tenths = (basisPoints + 5) / 10;
        return (tenths / 10) + "." + (tenths % 10) + "%";
    }
}
