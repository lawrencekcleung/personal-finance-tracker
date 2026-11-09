package io.github.lawrencekcleung.pft.config;

import io.github.lawrencekcleung.pft.model.Money;

import java.util.Objects;

/**
 * User-tunable settings.
 *
 * @param monthlyLimit the monthly expense budget used by {@code add} warnings
 */
public record Budget(Money monthlyLimit) {

    /** The built-in budget when no config file overrides it. */
    public static final Money DEFAULT_MONTHLY_LIMIT = Money.ofCents(50_000);

    /**
     * Validates the setting.
     *
     * @throws NullPointerException if the limit is missing
     */
    public Budget {
        Objects.requireNonNull(monthlyLimit, "monthlyLimit is required");
    }

    /**
     * Returns the built-in defaults.
     *
     * @return a budget of 500.00
     */
    public static Budget defaults() {
        return new Budget(DEFAULT_MONTHLY_LIMIT);
    }
}
