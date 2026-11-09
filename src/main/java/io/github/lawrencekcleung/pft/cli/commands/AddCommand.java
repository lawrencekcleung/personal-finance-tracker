package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.MoneyConverter;
import io.github.lawrencekcleung.pft.cli.RootCommand;
import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.service.TransactionService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.time.LocalDate;
import java.util.concurrent.Callable;

/**
 * {@code add} — records a single transaction.
 *
 * <p>Validation lives in {@link TransactionService}; the command only declares flags, hands the
 * typed values to the service and prints the stored id with the effective category.</p>
 */
@Command(name = "add", description = "record a single income or expense",
        mixinStandardHelpOptions = true)
public final class AddCommand implements Callable<Integer> {

    @ParentCommand
    private RootCommand root;

    @Option(names = "--date", required = true, paramLabel = "<yyyy-MM-dd>",
            description = "the day of the transaction")
    private LocalDate date;

    @Option(names = "--type", required = true, paramLabel = "<income|expense>",
            description = "income or expense")
    private String type;

    @Option(names = "--amount", required = true, paramLabel = "<amount>",
            description = "non-negative magnitude with at most 2 decimals",
            converter = MoneyConverter.class)
    private Money amount;

    @Option(names = "--description", required = true,
            description = "what the transaction was for")
    private String description;

    @Option(names = "--category", description = "category (default: UNCATEGORIZED)")
    private String category;

    @Override
    public Integer call() {
        try (AppContext ctx = root.context()) {
            Transaction stored = new TransactionService(ctx.repository()).add(
                    date, type, amount, description, category);
            ctx.out().println("Added #" + stored.id()
                    + " (category: " + stored.category() + ")");
        }
        return 0;
    }
}
