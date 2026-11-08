package io.github.lawrencekcleung.pft.model;

/**
 * The direction of a transaction: money coming in or money going out.
 *
 * <p>The enum carries the direction, while a {@link Money} always holds a non-negative
 * magnitude. An expense of 42.50 is therefore represented as {@link #EXPENSE} together with
 * {@code Money.ofCents(4250)}, never as a negative amount.</p>
 */
public enum TransactionType {

    /** Money received, for example a salary payment. */
    INCOME("income"),

    /** Money spent, for example a grocery purchase. */
    EXPENSE("expense");

    private final String wireName;

    TransactionType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The lower-case name used when the type is written to or read from storage.
     *
     * @return the wire name of this type
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves a type from its wire name.
     *
     * <p>The match is case-sensitive: {@code "income"} and {@code "expense"} are accepted,
     * while {@code "Income"} is not.</p>
     *
     * @param value the wire name to resolve
     * @return the matching type
     * @throws IllegalArgumentException if {@code value} is {@code null} or not a known wire name
     */
    public static TransactionType fromWire(String value) {
        for (TransactionType type : values()) {
            if (type.wireName.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown transaction type: " + value);
    }
}
