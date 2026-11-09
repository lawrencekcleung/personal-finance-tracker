package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.error.UsageException;
import picocli.CommandLine;

import java.io.PrintWriter;

/**
 * Maps command exceptions to the PRD's exit codes: usage errors are {@code 1}, data and storage
 * errors are {@code 2}. Unexpected exceptions are rethrown so bugs stay visible.
 */
final class ExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine,
                                        CommandLine.ParseResult parseResult) {
        Throwable cause = unwrap(ex);
        PrintWriter err = commandLine.getErr();
        if (cause instanceof UsageException) {
            err.println(cause.getMessage());
            return 1;
        }
        if (cause instanceof DataException || cause instanceof StorageException) {
            err.println(cause.getMessage());
            return 2;
        }
        throw new RuntimeException(ex);
    }

    private static Throwable unwrap(Throwable ex) {
        if (ex instanceof CommandLine.ExecutionException && ex.getCause() != null) {
            return ex.getCause();
        }
        return ex;
    }
}
