package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.CommandDefinition;
import io.github.lawrencekcleung.pft.cli.OptionSpec;
import io.github.lawrencekcleung.pft.cli.OptionType;
import io.github.lawrencekcleung.pft.cli.ParsedArgs;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.service.TransactionService;

import java.util.List;

/**
 * {@code add} — records a single transaction.
 *
 * <p>Validation lives in {@link TransactionService}; the command only declares flags, reads
 * typed values and prints the stored id with the effective category.</p>
 */
public final class AddCommand {

    private AddCommand() {
    }

    /**
     * Declares the command.
     *
     * @return the definition registered in {@link io.github.lawrencekcleung.pft.cli.CommandRegistry}
     */
    public static CommandDefinition definition() {
        return new CommandDefinition("add", "record a single income or expense",
                List.of(
                        new OptionSpec("date", "the day of the transaction",
                                OptionType.DATE, true, null),
                        new OptionSpec("type", "income or expense",
                                OptionType.STRING, true, null),
                        new OptionSpec("amount",
                                "non-negative magnitude with at most 2 decimals",
                                OptionType.AMOUNT, true, null),
                        new OptionSpec("description", "what the transaction was for",
                                OptionType.STRING, true, null),
                        new OptionSpec("category", "category (default: UNCATEGORIZED)",
                                OptionType.STRING, false, null)),
                AddCommand::run);
    }

    private static void run(ParsedArgs args, AppContext ctx) {
        Transaction stored = new TransactionService(ctx.repository()).add(
                args.requireDate("date"),
                args.requireString("type"),
                args.requireAmount("amount"),
                args.requireString("description"),
                args.optionalString("category").orElse(null));

        ctx.out().println("Added #" + stored.id() + " (category: " + stored.category() + ")");
    }
}
