package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.CommandDefinition;
import io.github.lawrencekcleung.pft.cli.OptionSpec;
import io.github.lawrencekcleung.pft.cli.OptionType;
import io.github.lawrencekcleung.pft.cli.ParsedArgs;
import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.rules.RuleRegistry;
import io.github.lawrencekcleung.pft.service.CategorizationService;
import io.github.lawrencekcleung.pft.service.CategoryChange;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@code categorize} — dry-runs or applies the keyword rules, and performs manual overrides.
 *
 * <p>Without {@code --apply} nothing is written: the planned changes are printed for review.
 * {@code --id N --category X} is a manual override and therefore writes immediately; combining it
 * with {@code --apply} assigns the override first and then categorizes the remaining rows.
 * Re-running {@code --apply} is a no-op once everything is categorized.</p>
 */
public final class CategorizeCommand {

    private CategorizeCommand() {
    }

    /**
     * Declares the command.
     *
     * @return the definition registered in {@link io.github.lawrencekcleung.pft.cli.CommandRegistry}
     */
    public static CommandDefinition definition() {
        return new CommandDefinition("categorize",
                "assign categories by rules, or override one transaction",
                List.of(
                        new OptionSpec("apply", "write the planned changes",
                                OptionType.FLAG, false, null),
                        new OptionSpec("id", "transaction to categorize manually",
                                OptionType.INT, false, null),
                        new OptionSpec("category", "category for --id",
                                OptionType.STRING, false, null)),
                CategorizeCommand::run);
    }

    private static void run(ParsedArgs args, AppContext ctx) {
        OptionalInt id = args.optionalInt("id");
        Optional<String> category = args.optionalString("category");
        boolean apply = args.flag("apply");

        if (id.isPresent() && category.isEmpty()) {
            throw new UsageException("--id requires --category");
        }
        if (id.isEmpty() && category.isPresent()) {
            throw new UsageException("--category requires --id");
        }

        CategorizationService service =
                new CategorizationService(ctx.repository(), RuleRegistry.defaults());

        boolean overridden = false;
        if (id.isPresent()) {
            Transaction updated = service.override(id.getAsInt(), category.orElseThrow());
            ctx.out().println("Categorized #" + updated.id() + " as "
                    + updated.category() + ".");
            overridden = true;
        }
        if (!apply && overridden) {
            return;
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
                return;
            }
            int applied = service.apply(plan);
            ctx.out().println("Categorized " + applied
                    + (applied == 1 ? " transaction." : " transactions."));
            return;
        }

        if (plan.isEmpty()) {
            ctx.out().println("No uncategorized transactions.");
            return;
        }
        ctx.out().println("Dry run: " + plan.size()
                + (plan.size() == 1 ? " transaction would be categorized."
                        : " transactions would be categorized.")
                + " Use --apply to write.");
    }
}
