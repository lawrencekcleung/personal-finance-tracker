package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.RootCommand;
import io.github.lawrencekcleung.pft.cli.SummaryFormatter;
import io.github.lawrencekcleung.pft.service.Summary;
import io.github.lawrencekcleung.pft.service.SummaryService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.time.YearMonth;
import java.util.concurrent.Callable;

/**
 * {@code summary} — prints totals and the expense breakdown for a month or all time.
 *
 * <p>Read-only: a missing store stays missing, and nothing is ever written.</p>
 */
@Command(name = "summary", description = "totals and spend by category",
        mixinStandardHelpOptions = true)
public final class SummaryCommand implements Callable<Integer> {

    @ParentCommand
    private RootCommand root;

    @Option(names = "--month", paramLabel = "<yyyy-MM>", description = "only this month")
    private YearMonth month;

    @Override
    public Integer call() {
        try (AppContext ctx = root.context()) {
            Summary summary = new SummaryService(ctx.repository()).summarize(month);
            ctx.out().println(new SummaryFormatter().format(summary));
        }
        return 0;
    }
}
