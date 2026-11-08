package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.CommandDefinition;
import io.github.lawrencekcleung.pft.cli.FindingPrinter;
import io.github.lawrencekcleung.pft.cli.OptionSpec;
import io.github.lawrencekcleung.pft.cli.OptionType;
import io.github.lawrencekcleung.pft.cli.ParsedArgs;
import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.rules.AnalysisContext;
import io.github.lawrencekcleung.pft.rules.AnalysisRule;
import io.github.lawrencekcleung.pft.rules.Finding;
import io.github.lawrencekcleung.pft.rules.Rule;
import io.github.lawrencekcleung.pft.rules.RuleRegistry;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code analyze} — runs the analysis rules and prints their findings.
 *
 * <p>Findings are ordered by rule id, then earliest transaction date, then transaction ids, so
 * output is stable regardless of rule iteration order. The command is read-only; findings are
 * recomputed on every run and never persisted.</p>
 */
public final class AnalyzeCommand {

    private AnalyzeCommand() {
    }

    /**
     * Declares the command.
     *
     * @return the definition registered in {@link io.github.lawrencekcleung.pft.cli.CommandRegistry}
     */
    public static CommandDefinition definition() {
        return new CommandDefinition("analyze", "run rule-based analysis",
                List.of(
                        new OptionSpec("month", "only this month (yyyy-MM)",
                                OptionType.YEAR_MONTH, false, null),
                        new OptionSpec("rule", "run only this rule",
                                OptionType.STRING, false, null)),
                AnalyzeCommand::run);
    }

    private static void run(ParsedArgs args, AppContext ctx) {
        YearMonth month = args.optionalMonth("month").orElse(null);
        RuleRegistry registry = RuleRegistry.defaults();
        List<AnalysisRule> rules = selectRules(registry, args.optionalString("rule"));

        List<Transaction> transactions = ctx.repository().findAll().stream()
                .filter(tx -> month == null || YearMonth.from(tx.date()).equals(month))
                .toList();
        AnalysisContext context = new AnalysisContext(transactions, month);
        Map<Integer, LocalDate> dates = transactions.stream()
                .collect(Collectors.toMap(Transaction::id, Transaction::date));

        List<Finding> findings = rules.stream()
                .flatMap(rule -> rule.evaluate(context).stream())
                .sorted((a, b) -> compare(a, b, dates))
                .toList();

        if (findings.isEmpty()) {
            ctx.out().println("No findings.");
            return;
        }
        FindingPrinter printer = new FindingPrinter();
        for (Finding finding : findings) {
            ctx.out().println(printer.format(finding));
        }
    }

    private static List<AnalysisRule> selectRules(RuleRegistry registry, Optional<String> id) {
        if (id.isEmpty()) {
            return registry.analysisRules();
        }
        AnalysisRule rule = registry.analysisRule(id.get()).orElseThrow(
                () -> new UsageException("unknown rule '" + id.get() + "'; available: "
                        + availableRules(registry)));
        return List.of(rule);
    }

    private static String availableRules(RuleRegistry registry) {
        if (registry.analysisRules().isEmpty()) {
            return "none";
        }
        return registry.analysisRules().stream().map(Rule::id).collect(Collectors.joining(", "));
    }

    private static int compare(Finding first, Finding second, Map<Integer, LocalDate> dates) {
        int byRule = first.ruleId().compareTo(second.ruleId());
        if (byRule != 0) {
            return byRule;
        }
        int byDate = earliestDate(first, dates).compareTo(earliestDate(second, dates));
        if (byDate != 0) {
            return byDate;
        }
        List<Integer> left = first.transactionIds();
        List<Integer> right = second.transactionIds();
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int byId = Integer.compare(left.get(i), right.get(i));
            if (byId != 0) {
                return byId;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static LocalDate earliestDate(Finding finding, Map<Integer, LocalDate> dates) {
        return finding.transactionIds().stream()
                .map(dates::get)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.MAX);
    }
}
