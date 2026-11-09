package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.RootCommand;
import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.service.ImportError;
import io.github.lawrencekcleung.pft.service.ImportReport;
import io.github.lawrencekcleung.pft.service.ImportService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code import} — bulk-loads transactions from a CSV file.
 *
 * <p>Valid rows are committed in one write. Every skipped row is reported to stderr as
 * {@code file:N: reason}; a partial import then fails with exit {@code 2}, while a clean import
 * exits {@code 0}.</p>
 */
@Command(name = "import", description = "bulk import transactions from a CSV file",
        mixinStandardHelpOptions = true)
public final class ImportCommand implements Callable<Integer> {

    @ParentCommand
    private RootCommand root;

    @Option(names = "--file", required = true, paramLabel = "<csv>",
            description = "source CSV file")
    private Path file;

    @Override
    public Integer call() {
        try (AppContext ctx = root.context()) {
            ImportReport report = new ImportService(ctx.repository()).importCsv(read(file));

            ctx.out().println("Imported " + report.imported()
                    + (report.imported() == 1 ? " transaction." : " transactions."));
            for (ImportError error : report.errors()) {
                ctx.err().println(file + ":" + error.lineNumber() + ": " + error.message());
            }
            if (report.hasErrors()) {
                throw new DataException("Skipped " + report.skipped()
                        + (report.skipped() == 1 ? " row." : " rows."));
            }
        }
        return 0;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }
}
