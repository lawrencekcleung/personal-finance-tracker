package io.github.lawrencekcleung.pft.cli;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.util.List;

/**
 * Rewrites parse failures for humans: the problem, a spelling suggestion when picocli has one,
 * and the command to run for usage help.
 */
final class ParameterExceptionHandler implements CommandLine.IParameterExceptionHandler {

    @Override
    public int handleParseException(CommandLine.ParameterException ex, String[] args) {
        CommandLine commandLine = ex.getCommandLine();
        PrintWriter err = commandLine.getErr();
        err.println(ex.getMessage());

        if (ex instanceof CommandLine.UnmatchedArgumentException unmatched) {
            List<String> suggestions = unmatched.getSuggestions();
            if (!suggestions.isEmpty()) {
                err.println("Did you mean: " + String.join(", ", suggestions) + "?");
            }
        }
        err.println("Run '" + commandLine.getCommandSpec().qualifiedName()
                + " --help' for usage.");
        return 1;
    }
}
