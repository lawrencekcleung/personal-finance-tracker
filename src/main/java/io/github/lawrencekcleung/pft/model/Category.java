package io.github.lawrencekcleung.pft.model;

/**
 * Category vocabulary for transactions.
 *
 * <p>Categories are free-form lower-case strings such as {@code "groceries"} or
 * {@code "transport"}. A transaction keeps the {@link #UNCATEGORIZED} sentinel until a
 * categorization rule or a manual override assigns it a real category.</p>
 *
 * <p>This class is a namespace only and cannot be instantiated.</p>
 */
public final class Category {

    /**
     * The sentinel category used for transactions that no rule or manual override has
     * categorized yet.
     */
    public static final String UNCATEGORIZED = "UNCATEGORIZED";

    private Category() {
    }
}
