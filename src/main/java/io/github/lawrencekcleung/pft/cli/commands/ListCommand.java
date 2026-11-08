package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.CommandDefinition;
import io.github.lawrencekcleung.pft.cli.OptionSpec;
import io.github.lawrencekcleung.pft.cli.OptionType;
import io.github.lawrencekcleung.pft.cli.ParsedArgs;
import io.github.lawrencekcleung.pft.cli.TablePrinter;
import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.service.TransactionService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code list} — prints transactions in date-then-id order, optionally filtered.
 *
 * <p>Filters are applied by {@link TransactionService}; the command owns only presentation and
 * the empty-result message.</p>
 */
public final class ListCommand {

    private static final List<String> HEADERS =
            List.of("ID", "Date", "Type", "Amount", "Category", "Description");

    private ListCommand() {
    }

    /**
     * Declares the command.
     *
     * @return the definition registered in {@link io.github.lawrencekcleung.pft.cli.CommandRegistry}
     */
    public static CommandDefinition definition() {
        return new CommandDefinition("list", "list transactions, optionally filtered",
                List.of(
                        new OptionSpec("month", "only this month (yyyy-MM)",
                                OptionType.YEAR_MONTH, false, null),
                        new OptionSpec("category", "only this category",
                                OptionType.STRING, false, null),
                        new OptionSpec("uncategorized", "only transactions with no category",
                                OptionType.FLAG, false, null)),
                ListCommand::run);
    }

    private static void run(ParsedArgs args, AppContext ctx) {
        String category = args.optionalString("category").orElse(null);
        boolean uncategorized = args.flag("uncategorized");
        if (category != null && uncategorized) {
            throw new UsageException("use either --category or --uncategorized, not both");
        }

        List<Transaction> transactions = new TransactionService(ctx.repository()).list(
                args.optionalMonth("month").orElse(null), category, uncategorized);
        if (transactions.isEmpty()) {
            ctx.out().println("No transactions.");
            return;
        }

        List<List<String>> rows = new ArrayList<>(transactions.size());
        for (Transaction tx : transactions) {
            rows.add(List.of(
                    Integer.toString(tx.id()),
                    tx.date().toString(),
                    tx.type().wireName(),
                    tx.amount().format(),
                    tx.category(),
                    tx.description()));
        }
        ctx.out().println(new TablePrinter().render(HEADERS, rows, 0, 3));
    }
}
