package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.RootCommand;
import io.github.lawrencekcleung.pft.cli.TablePrinter;
import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.service.TransactionService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code list} — prints transactions in date-then-id order, optionally filtered.
 *
 * <p>Filters are applied by {@link TransactionService}; the command owns only presentation and
 * the empty-result message.</p>
 */
@Command(name = "list", description = "list transactions, optionally filtered",
        mixinStandardHelpOptions = true)
public final class ListCommand implements Callable<Integer> {

    private static final List<String> HEADERS =
            List.of("ID", "Date", "Type", "Amount", "Category", "Description");

    @ParentCommand
    private RootCommand root;

    @Option(names = "--month", paramLabel = "<yyyy-MM>", description = "only this month")
    private YearMonth month;

    @Option(names = "--category", description = "only this category")
    private String category;

    @Option(names = "--uncategorized", description = "only transactions with no category")
    private boolean uncategorized;

    @Override
    public Integer call() {
        if (category != null && uncategorized) {
            throw new UsageException("use either --category or --uncategorized, not both");
        }

        try (AppContext ctx = root.context()) {
            List<Transaction> transactions = new TransactionService(ctx.repository())
                    .list(month, category, uncategorized);
            if (transactions.isEmpty()) {
                ctx.out().println("No transactions.");
                return 0;
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
        return 0;
    }
}
