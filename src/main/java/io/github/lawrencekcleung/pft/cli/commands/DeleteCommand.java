package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.CommandDefinition;
import io.github.lawrencekcleung.pft.cli.OptionSpec;
import io.github.lawrencekcleung.pft.cli.OptionType;
import io.github.lawrencekcleung.pft.cli.ParsedArgs;
import io.github.lawrencekcleung.pft.service.TransactionService;

import java.util.List;

/**
 * {@code delete} — removes one transaction.
 *
 * <p>Deletion rewrites the store but never renumbers ids, and the deleted id is not reused by a
 * later insert. Unknown ids are a data error (exit {@code 2}) and leave the file untouched.</p>
 */
public final class DeleteCommand {

    private DeleteCommand() {
    }

    /**
     * Declares the command.
     *
     * @return the definition registered in {@link io.github.lawrencekcleung.pft.cli.CommandRegistry}
     */
    public static CommandDefinition definition() {
        return new CommandDefinition("delete", "remove a transaction",
                List.of(new OptionSpec("id", "transaction to remove",
                        OptionType.INT, true, null)),
                DeleteCommand::run);
    }

    private static void run(ParsedArgs args, AppContext ctx) {
        int id = args.requireInt("id");
        new TransactionService(ctx.repository()).delete(id);
        ctx.out().println("Deleted #" + id + ".");
    }
}
