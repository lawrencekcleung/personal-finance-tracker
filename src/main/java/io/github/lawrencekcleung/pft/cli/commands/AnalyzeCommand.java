package io.github.lawrencekcleung.pft.cli.commands;

import io.github.lawrencekcleung.pft.cli.AppContext;
import io.github.lawrencekcleung.pft.cli.FindingPrinter;
import io.github.lawrencekcleung.pft.cli.RootCommand;
import io.github.lawrencekcleung.pft.error.UsageException;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.rules.AnalysisContext;
import io.github.lawrencekcleung.pft.rules.AnalysisRule;
import io.github.lawrencekcleung.pft.rules.Finding;
import io.github.lawrencekcleung.pft.rules.Rule;
import io.github.lawrencekcleung.pft.rules.RuleRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * {@code analyze} — runs the analysis rules and prints their findings.
 *
 * <p>Findings are ordered by rule id, then earliest transaction date, then transaction ids, so
 * output is stable regardless of rule iteration order. The command is read-only; findings are
 * recomputed on every run and never persisted.</p>
 */
@Command(name = "analyze", description = "run rule-based analysis",
        mixinStandardHelpOptions = true)
public final class AnalyzeCommand implements Callable<Integer> {

    @ParentCommand
    private RootCommand root;

    @Option(names = "--month", paramLabel = "<yyyy-MM>", description = "only this month")
    private YearMonth month;

    @Option(names = "--rule", description = "run only this rule")
    private String ruleId;

    @Override
    public Integer call() {
        RuleRegistry registry = RuleRegistry.defaults();
        List<AnalysisRule> rules = selectRules(registry, ruleId);

        try (AppContext ctx = root.context()) {
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
                return 0;
            }
            FindingPrinter printer = new FindingPrinter();
            for (Finding finding : findings) {
                ctx.out().println(printer.format(finding));
            }
        }
        return 0;
    }

    private static List<AnalysisRule> selectRules(RuleRegistry registry, String id) {
        if (id == null) {
            return registry.analysisRules();
        }
        AnalysisRule rule = registry.analysisRule(id).orElseThrow(
                () -> new UsageException("unknown rule '" + id + "'; available: "
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
