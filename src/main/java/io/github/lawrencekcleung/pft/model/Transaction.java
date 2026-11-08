package io.github.lawrencekcleung.pft.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single recorded income or expense.
 *
 * <p>{@code amount} is a non-negative magnitude and {@code type} carries the direction, so an
 * expense of 42.50 is {@link TransactionType#EXPENSE} with {@code Money.ofCents(4250)}. The
 * {@code id} is assigned by the repository when the transaction is stored.</p>
 *
 * @param id          repository-assigned identifier
 * @param date        the day the transaction occurred
 * @param type        the direction of the money movement
 * @param amount      the non-negative magnitude of the movement
 * @param category    the category, or {@link Category#UNCATEGORIZED} when none is assigned
 * @param description a non-blank human-readable description
 */
public record Transaction(int id, LocalDate date, TransactionType type, Money amount,
                          String category, String description) {

    /**
     * Validates and normalizes the components.
     *
     * @throws NullPointerException     if {@code date}, {@code type}, {@code amount} or
     *                                  {@code description} is {@code null}
     * @throws IllegalArgumentException if {@code amount} is negative or {@code description} is
     *                                  blank
     */
    public Transaction {
        Objects.requireNonNull(date, "date is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(amount, "amount is required");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("amount must be non-negative; use type for direction");
        }
        Objects.requireNonNull(description, "description is required");
        description = description.trim();
        if (description.isEmpty()) {
            throw new IllegalArgumentException("description is required");
        }
        if (category == null || category.isBlank()) {
            category = Category.UNCATEGORIZED;
        } else {
            category = category.trim();
        }
    }

    /**
     * Returns whether this transaction is an expense.
     *
     * @return {@code true} when the type is {@link TransactionType#EXPENSE}
     */
    public boolean isExpense() {
        return type == TransactionType.EXPENSE;
    }

    /**
     * Returns whether this transaction is income.
     *
     * @return {@code true} when the type is {@link TransactionType#INCOME}
     */
    public boolean isIncome() {
        return type == TransactionType.INCOME;
    }
}
