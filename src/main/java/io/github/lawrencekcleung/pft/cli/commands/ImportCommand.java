package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.CommandDefinition;
import io.github.lawrencekcleung.pft.cli.OptionSpec;
import io.github.lawrencekcleung.pft.cli.OptionType;
import io.github.lawrencekcleung.pft.cli.ParsedArgs;
import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.service.ImportError;
import io.github.lawrencekcleung.pft.service.ImportReport;
import io.github.lawrencekcleung.pft.service.ImportService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code import} — bulk-loads transactions from a CSV file.
 *
 * <p>Valid rows are committed in one write. Every skipped row is reported to stderr as
 * {@code file:N: reason}; a partial import then fails with exit {@code 2}, while a clean import
 * exits {@code 0}.</p>
 */
public final class ImportCommand {

    private ImportCommand() {
    }

    /**
     * Declares the command.
     *
     * @return the definition registered in {@link io.github.lawrencekcleung.pft.cli.CommandRegistry}
     */
    public static CommandDefinition definition() {
        return new CommandDefinition("import", "bulk import transactions from a CSV file",
                List.of(new OptionSpec("file", "source CSV file",
                        OptionType.PATH, true, null)),
                ImportCommand::run);
    }

    private static void run(ParsedArgs args, AppContext ctx) {
        Path file = args.requirePath("file");
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

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }
}
