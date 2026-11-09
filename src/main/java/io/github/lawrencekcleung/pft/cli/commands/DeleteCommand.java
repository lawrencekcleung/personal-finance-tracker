package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.RootCommand;
import io.github.lawrencekcleung.pft.service.TransactionService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * {@code delete} — removes one transaction.
 *
 * <p>Deletion rewrites the store but never renumbers ids, and the deleted id is not reused by a
 * later insert. Unknown ids are a data error (exit {@code 2}) and leave the file untouched.</p>
 */
@Command(name = "delete", description = "remove a transaction",
        mixinStandardHelpOptions = true)
public final class DeleteCommand implements Callable<Integer> {

    @ParentCommand
    private RootCommand root;

    @Option(names = "--id", required = true, paramLabel = "<n>",
            description = "transaction to remove")
    private int id;

    @Override
    public Integer call() {
        try (AppContext ctx = root.context()) {
            new TransactionService(ctx.repository()).delete(id);
            ctx.out().println("Deleted #" + id + ".");
        }
        return 0;
    }
}
