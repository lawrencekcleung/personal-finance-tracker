package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.store.TransactionRepository;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

/**
 * Everything a command needs to do its work without knowing how the CLI was assembled.
 *
 * <p>The repository hides the storage format, {@code out}/{@code err} keep commands printable in
 * tests, and the clock keeps date-dependent behaviour deterministic.</p>
 */
public final class AppContext implements AutoCloseable {

    private final TransactionRepository repository;
    private final PrintWriter out;
    private final PrintWriter err;
    private final Clock clock;
    private final Path dataPath;
    private final Money monthlyBudget;

    /**
     * Creates the context.
     *
     * @param repository    the open store
     * @param out           standard output
     * @param err           standard error
     * @param clock         the clock used for defaults such as "this month"
     * @param dataPath      the resolved data file, for diagnostics
     * @param monthlyBudget the monthly expense budget used by warnings
     */
    public AppContext(TransactionRepository repository, PrintWriter out, PrintWriter err,
                      Clock clock, Path dataPath, Money monthlyBudget) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.out = Objects.requireNonNull(out, "out is required");
        this.err = Objects.requireNonNull(err, "err is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.dataPath = Objects.requireNonNull(dataPath, "dataPath is required");
        this.monthlyBudget = Objects.requireNonNull(monthlyBudget, "monthlyBudget is required");
    }

    /** Returns the open store. */
    public TransactionRepository repository() {
        return repository;
    }

    /** Returns standard output. */
    public PrintWriter out() {
        return out;
    }

    /** Returns standard error. */
    public PrintWriter err() {
        return err;
    }

    /** Returns the clock used for date defaults. */
    public Clock clock() {
        return clock;
    }

    /** Returns the resolved data file path. */
    public Path dataPath() {
        return dataPath;
    }

    /** Returns the configured monthly expense budget. */
    public Money monthlyBudget() {
        return monthlyBudget;
    }

    /** Closes the store. */
    @Override
    public void close() {
        repository.close();
    }
}
