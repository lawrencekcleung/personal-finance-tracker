package io.github.lawrencekcleung.pft.rules;

import io.github.lawrencekcleung.pft.model.Transaction;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Flags pairs of expenses that look like the same charge made twice.
 *
 * <p>Two distinct expenses form a pair when their descriptions match after trimming, lower-casing
 * and collapsing internal whitespace; their amounts are equal in cents; and their dates are at
 * most {@code windowDays} apart. Same-day pairs are allowed. A transaction may appear in more than
 * one pair, which keeps the rule simple and deterministic.</p>
 *
 * <p>Pairs are sorted by earliest date, then by transaction ids. Income is never considered.</p>
 */
public final class DuplicateChargeRule implements AnalysisRule {

    private final int windowDays;

    /**
     * Creates the rule.
     *
     * @param windowDays the maximum number of days between two matching charges
     * @throws IllegalArgumentException if the window is negative
     */
    public DuplicateChargeRule(int windowDays) {
        if (windowDays < 0) {
            throw new IllegalArgumentException("window must be non-negative: " + windowDays);
        }
        this.windowDays = windowDays;
    }

    @Override
    public String id() {
        return "duplicate-charge";
    }

    @Override
    public String description() {
        return "flags equal expense charges within " + windowDays + " days";
    }

    @Override
    public List<Finding> evaluate(AnalysisContext ctx) {
        List<Transaction> expenses = ctx.expenses().stream()
                .sorted(Comparator.comparingInt(Transaction::id))
                .toList();

        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < expenses.size(); i++) {
            for (int j = i + 1; j < expenses.size(); j++) {
                Transaction first = expenses.get(i);
                Transaction second = expenses.get(j);
                if (isDuplicate(first, second)) {
                    pairs.add(new Pair(first, second));
                }
            }
        }

        pairs.sort(Comparator.comparing((Pair pair) -> earliest(pair).date())
                .thenComparingInt(pair -> pair.first().id())
                .thenComparingInt(pair -> pair.second().id()));

        return pairs.stream().map(this::toFinding).toList();
    }

    private boolean isDuplicate(Transaction first, Transaction second) {
        if (!normalize(first.description()).equals(normalize(second.description()))) {
            return false;
        }
        if (!first.amount().equals(second.amount())) {
            return false;
        }
        long days = Math.abs(ChronoUnit.DAYS.between(first.date(), second.date()));
        return days <= windowDays;
    }

    private Finding toFinding(Pair pair) {
        Transaction earlier = earliest(pair);
        Transaction later = earlier == pair.first() ? pair.second() : pair.first();
        long days = ChronoUnit.DAYS.between(earlier.date(), later.date());

        String message = pair.first().amount().format()
                + " \"" + earlier.description() + "\""
                + " on " + earlier.date()
                + " and " + later.date()
                + " (" + days + (days == 1 ? " day apart)" : " days apart)");
        return new Finding(id(), List.of(pair.first().id(), pair.second().id()), message);
    }

    private static Transaction earliest(Pair pair) {
        return pair.first().date().isAfter(pair.second().date())
                ? pair.second()
                : pair.first();
    }

    private static String normalize(String description) {
        return description.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private record Pair(Transaction first, Transaction second) {
    }
}
