package io.github.lawrencekcleung.pft.error;

/**
 * Signals a corrupt or unwritable store; maps to exit code 2.
 */
public final class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
