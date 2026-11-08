package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.CommandDefinition;
import io.github.lawrencekcleung.pft.cli.OptionSpec;
import io.github.lawrencekcleung.pft.cli.OptionType;
import io.github.lawrencekcleung.pft.cli.ParsedArgs;
import io.github.lawrencekcleung.pft.cli.SummaryFormatter;
import io.github.lawrencekcleung.pft.service.Summary;
import io.github.lawrencekcleung.pft.service.SummaryService;

import java.time.YearMonth;
import java.util.List;

/**
 * {@code summary} — prints totals and the expense breakdown for a month or all time.
 *
 * <p>Read-only: a missing store stays missing, and nothing is ever written.</p>
 */
public final class SummaryCommand {

    private SummaryCommand() {
    }

    /**
     * Declares the command.
     *
     * @return the definition registered in {@link io.github.lawrencekcleung.pft.cli.CommandRegistry}
     */
    public static CommandDefinition definition() {
        return new CommandDefinition("summary", "totals and spend by category",
                List.of(new OptionSpec("month", "only this month (yyyy-MM)",
                        OptionType.YEAR_MONTH, false, null)),
                SummaryCommand::run);
    }

    private static void run(ParsedArgs args, AppContext ctx) {
        YearMonth month = args.optionalMonth("month").orElse(null);
        Summary summary = new SummaryService(ctx.repository()).summarize(month);
        ctx.out().println(new SummaryFormatter().format(summary));
    }
}
