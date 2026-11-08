package io.github.lawrencekcleung.pft.service;

/**
 * One source row that could not be imported, with the 1-based line it started on.
 *
 * @param lineNumber the line in the import file
 * @param message    what is wrong, without a file or line prefix
 */
public record ImportError(int lineNumber, String message) {

    /**
     * Validates the error.
     *
     * @throws IllegalArgumentException if the line number is below 1
     */
    public ImportError {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("line number must be positive: " + lineNumber);
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
    }
}
