package io.github.lawrencekcleung.pft.error;

/**
 * Signals bad flags or an invalid CLI invocation; maps to exit code 1.
 */
public final class UsageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UsageException(String message) {
        super(message);
    }

    public UsageException(String message, Throwable cause) {
        super(message, cause);
    }
}
