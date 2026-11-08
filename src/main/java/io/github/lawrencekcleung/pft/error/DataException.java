package io.github.lawrencekcleung.pft.error;

/**
 * Signals an invalid value, unknown id, or partial import; maps to exit code 2.
 */
public final class DataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataException(String message) {
        super(message);
    }

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }
}
