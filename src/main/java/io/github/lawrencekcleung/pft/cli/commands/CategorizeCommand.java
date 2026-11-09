package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.RootCommand;
import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.rules.RuleRegistry;
import io.github.lawrencekcleung.pft.service.CategorizationService;
import io.github.lawrencekcleung.pft.service.CategoryChange;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code categorize} — dry-runs or applies the keyword rules, and performs manual overrides.
 *
 * <p>Without {@code --apply} nothing is written: the planned changes are printed for review.
 * {@code --id N --category X} is a manual override and therefore writes immediately; combining it
 * with {@code --apply} assigns the override first and then categorizes the remaining rows.
 * Re-running {@code --apply} is a no-op once everything is categorized.</p>
 */
@Command(name = "categorize",
        description = "assign categories by rules, or override one transaction",
        mixinStandardHelpOptions = true)
public final class CategorizeCommand implements Callable<Integer> {

    @ParentCommand
    private RootCommand root;

    @Option(names = "--apply", description = "write the planned changes")
    private boolean apply;

    @Option(names = "--id", paramLabel = "<n>",
            description = "transaction to categorize manually")
    private Integer id;

    @Option(names = "--category", description = "category for --id")
    private String category;

    @Override
    public Integer call() {
        if (id != null && category == null) {
            throw new UsageException("--id requires --category");
        }
        if (id == null && category != null) {
            throw new UsageException("--category requires --id");
        }

        try (AppContext ctx = root.context()) {
            CategorizationService service =
                    new CategorizationService(ctx.repository(), RuleRegistry.defaults());

            boolean overridden = false;
            if (id != null) {
                Transaction updated = service.override(id, category);
                ctx.out().println("Categorized #" + updated.id() + " as "
                        + updated.category() + ".");
                overridden = true;
            }
            if (!apply && overridden) {
                return 0;
            }

            List<CategoryChange> plan = service.plan();
            for (CategoryChange change : plan) {
                ctx.out().println("#" + change.transaction().id()
                        + "  " + change.from() + " -> " + change.to()
                        + "  \"" + change.transaction().description() + "\"");
            }

            if (apply) {
                if (plan.isEmpty()) {
                    if (!overridden) {
                        ctx.out().println("No uncategorized transactions.");
                    }
                    return 0;
                }
                int applied = service.apply(plan);
                ctx.out().println("Categorized " + applied
                        + (applied == 1 ? " transaction." : " transactions."));
                return 0;
            }

            if (plan.isEmpty()) {
                ctx.out().println("No uncategorized transactions.");
                return 0;
            }
            ctx.out().println("Dry run: " + plan.size()
                    + (plan.size() == 1 ? " transaction would be categorized."
                            : " transactions would be categorized.")
                    + " Use --apply to write.");
        }
        return 0;
    }
}
