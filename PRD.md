# PRD — `pft` Personal Finance Tracker (CLI)

| | |
|---|---|
| Status | Approved for implementation |
| Source | Take-home exercise brief (kept outside this repo) |
| Language / build | Java 21, Gradle 9.x (Kotlin DSL), JUnit 5 |
| Storage | Flat CSV file behind a swappable repository interface |
| Runtime dependencies | None |
| Estimate | ~7h35m human effort (upper bound, incl. tests and docs) |

---

## 1. Problem and goals

Build a CLI tool that tracks one individual's income and expenses, lets them import and enter transactions, categorize them on demand, and get a summary. One rule-based analysis is required, and it must **not** be the "flag transactions over $50" example.

Goals, in priority order:

1. **Works end to end** from import → categorize → summarize, runnable in two commands.
2. **Readable and small.** A reviewer can hold the whole thing in their head.
3. **Testable and extensible** without ceremony: new command, new option, new rule, new storage adapter each have a documented, one-file-shaped recipe.
4. **Honest scope.** Assumptions stated; nothing half-built pretending to be finished.

Non-goals: multi-user, authentication, multi-currency, budgets, cloud sync, UI, recurring transaction generation, performance beyond personal-scale data.

### Requirement traceability

| Requirement (take_home.md) | Where satisfied |
|---|---|
| CLI tool, personal income/expense, single user | §5 command surface, §6 domain |
| Data entry + import for one or more transactions | `add`, `import` (§5.1, §5.2) |
| On-demand categorization | `categorize` dry-run / `--apply` / `--id` (§9.2) |
| Summary: total spend, spend by category | `summary` (§5.6, §6.4) |
| One rule-based analysis, not the $50 example | `duplicate-charge` analysis rule (§9.3) |
| Usable as a tracker, assumptions stated | §4 assumptions, §7 persistence, §13 README |
| Readable, maintainable, testable, extensible | §8 CLI framework, §9 rule framework, §7.4 seam, §12 testing |
| Source/text only, no binaries | §10 build (no `gradlew`, no committed data) |

---

## 2. Locked decisions

| Decision | Choice | Rationale |
|---|---|---|
| Language | Java 21 | Strongest JVM option; records, `switch` expressions, `sealed`, text blocks |
| Build | Gradle 9.x, Kotlin DSL, toolchain 21 | Requested over Maven; `application` plugin gives `installDist` launcher |
| Wrapper | **Not committed** | `gradlew` bundles a jar; the brief asks for text only. README states Gradle 8.5+ is required and shows how to generate a wrapper locally |
| Dependencies | Zero at runtime; JUnit 5 for tests | Keeps the repo text-only and the build network-free |
| Persistence | CSV behind `TransactionRepository` | Human-readable, diffable, eyeball-able in an interview; seam allows SQLite later without touching services |
| Money | `long` cents in a `Money` record | Exact, no rounding modes, no `BigDecimal.equals` trap. `BigDecimal` deliberately avoided |
| Direction | `TransactionType` (income/expense) carries sign; amounts stored non-negative | Keeps money free of sign rules; one `partitioningBy` for totals |
| Categories | Free-form `String`, default `UNCATEGORIZED` | Extending needs no code change; not an enum |
| Categorization | Keyword rules, first match wins, only touches `UNCATEGORIZED` | Deterministic, idempotent, manual assignment always wins |
| Analysis rule | `duplicate-charge` | Not the provided example; genuinely useful; easy to test at boundaries |
| Rule registration | Explicit `RuleRegistry` lists | Discoverable, ordered, testable. `ServiceLoader` rejected as indirection without benefit |
| Rejected | `BigDecimal` amounts, `double` amounts, H2/SQLite now, reflection-based command dispatch, config files for rules | Simplest thing that meets the brief |

---

## 3. Architecture overview

```
CLI (ArgParser + CommandRegistry)
  └── Commands (one class per verb) ──> Services
                                          ├── TransactionService   (add/import/list/delete)
                                          ├── CategorizationService (dry-run/apply/override)
                                          └── SummaryService        (totals, by category)
                                                │
                        RuleRegistry ───────────┤
                          ├── CategorizationRule (keyword → category)
                          └── AnalysisRule       (duplicate-charge)
                                                │
                                     TransactionRepository (interface)
                                       └── CsvTransactionRepository
                                                │
                                     data/transactions.csv
```

Dependency rule: **only `RepositoryFactory` knows about files.** Services take `TransactionRepository`; they never see a `Path`. If a service signature ever grows a `Path`, the storage abstraction has leaked.

---

## 4. Assumptions (goes verbatim into the README)

1. **Single user, single currency, single process.** No concurrency control beyond atomic file replacement. Two simultaneous invocations may lose one write; out of scope.
2. **No currency symbol.** Amounts are printed as bare 2dp text (`1234.50`); currency is whatever the user means. Amounts with more than 2 decimal places are rejected, never rounded silently.
3. **Dates are ISO `yyyy-MM-dd`**, no timezone. "Month" means the calendar month.
4. **Data file is local**: `--data <path>` (highest precedence) → `PFT_DATA` env var → `./data/transactions.csv`.
5. **Import does not deduplicate.** Importing the same file twice creates duplicates — which `analyze` will then flag. This is intentional and documented.
6. **IDs are opaque, monotonic, and never reused.** They are not row numbers.
7. **Analysis is advisory and read-only.** Findings are recomputed each run; nothing is persisted.
8. **Categorization rules live in code**, documented in the README. No config file (see §15 for the extension point).
9. **Personal scale.** Full-file rewrite per mutation is acceptable; hundreds to low thousands of transactions.
10. **Amount magnitude.** Realistic amounts are under 5,000,000.00 = 5×10⁸ cents, ~10 orders of magnitude inside `long`. Overflow still throws rather than wrapping.
11. **Out of scope:** auth, accounts, budgets, editing an amount (delete + re-add), multi-currency, exports beyond CSV, i18n.

---

## 5. CLI surface

### 5.0 Global options and conventions

```
pft [--data <path>] <command> [options]      # --data may also follow the command
pft --help
pft <command> --help
```

- Options accept `--name value` and `--name=value`.
- Unknown option → usage error listing the valid options for that command.
- Exit codes: `0` success; `1` usage error (bad flags, missing required option); `2` data error (invalid value, unknown id, corrupt store, partial import).
- All output goes to stdout; errors go to stderr.
- Help text is generated from the option specifications, so it cannot drift.

### 5.1 `add` — single transaction

```
pft add --date 2026-11-01 --type expense --amount 42.50 \
        --description "Countdown, Auckland" [--category groceries]
```

- `--date` required, ISO.
- `--type` required, `income|expense`.
- `--amount` required, non-negative, ≤2dp.
- `--description` required, trimmed, non-blank.
- `--category` optional; absent means `UNCATEGORIZED`.
- Prints `Added #<id>` plus the effective category:

  ```
  $ pft add --date 2026-11-01 --type expense --amount 42.50 --description "Countdown, Auckland"
  Added #1 (category: UNCATEGORIZED)
  ```

### 5.2 `import` — bulk entry

```
pft import --file nov.csv
```

Source schema: `date,type,amount,description[,category]`. The header is optional: it is
recognised when the first field of the first non-blank line is `date` (case-insensitive), and a
first field that parses as a date means the line is data. A leading `id` column is ignored; ids
are always reassigned.

- Per-line validation; bad rows are skipped and reported with **source line numbers**.
- Valid rows are committed in one write.
- Exit `0` if all rows imported, `2` if some were skipped.
- A skipped row consumes no id, so ids stay gapless.

```console
$ pft import --file nov.csv
Imported 4 transactions.
nov.csv:6: invalid amount 'abc': expected a non-negative number with at most 2 decimal places
Skipped 1 row.
```

### 5.3 `list`

```
pft list [--month 2026-11] [--category groceries] [--uncategorized]
```

Sorted by date ascending, then id ascending, independent of file order. Prints a fixed-width table:

```
$ pft list
ID  Date        Type      Amount  Category       Description
 1  2026-11-01  income   5000.00  income         Salary Oct
 2  2026-11-01  expense    42.50  groceries      Countdown, Auckland
```

- `--month` and `--category` combine; `--uncategorized` is shorthand for
  `--category UNCATEGORIZED` and cannot be combined with `--category`.
- An empty result prints exactly `No transactions.` and exits `0`.

### 5.4 `categorize`

```
pft categorize                      # dry run: show intended changes, write nothing
pft categorize --apply              # apply keyword rules to UNCATEGORIZED rows
pft categorize --id 7 --category client-entertainment   # manual override
```

- `--apply` with nothing to do prints `No uncategorized transactions.` and performs **no write** (idempotent, not a no-op rewrite).
- Manual assignment is final: later `--apply` runs skip it.
- Unknown id → exit `2`, no write.
- `--id` implies a write even without `--apply`; combining it with `--apply` assigns the override
  first, then categorizes the remaining rows.

```
$ pft categorize
#2  UNCATEGORIZED -> transport  "Uber to airport"
#3  UNCATEGORIZED -> subscriptions  "Spotify"
Dry run: 2 transactions would be categorized. Use --apply to write.

$ pft categorize --apply
#2  UNCATEGORIZED -> transport  "Uber to airport"
#3  UNCATEGORIZED -> subscriptions  "Spotify"
Categorized 2 transactions.

$ pft categorize --id 4 --category client-entertainment
Categorized #4 as client-entertainment.
```

### 5.5 `delete`

```
pft delete --id 3
```

Removes the row, rewrites the file, does not renumber. Prints `Deleted #3.`. Unknown id → exit
`2`, no write. The deleted id is never reused.

### 5.6 `summary`

```
pft summary [--month 2026-11]
```

```
Period: 2026-11   Income: 5000.00   Expenses: 184.60   Net: +4815.40

Category                     Spend     Share
groceries                   110.90     60.1%
client-entertainment         26.10     14.1%
dining                       19.80     10.7%
transport                    15.80      8.6%
subscriptions                12.00      6.5%
```

- Default range = all time, shown as `Period: all time`; `--month` filters.
- A period with no transactions prints exactly `No transactions.`.
- An income-only period prints the totals line without a category table.
- Net is signed (`+`/`-`); income and expenses are shown as magnitudes.
- Category rows sorted by spend descending, tie-broken by category name.
- Percentages are computed with integer basis points and displayed to 1dp; they may not total exactly 100.0% due to display rounding (noted in README).

### 5.7 `analyze`

```
pft analyze [--month 2026-11] [--rule duplicate-charge]
```

```
[duplicate-charge] #8, #9  9.90 "Coffee" on 2026-11-06 and 2026-11-07 (1 day apart)
```

- No `--rule` runs every registered analysis rule.
- Findings sorted deterministically: rule id, then earliest transaction date, then transaction ids.
- No findings prints `No findings.` and exits `0`.
- Read-only; never writes.

---

## 6. Domain model

### 6.1 `Money` — integer cents

```java
package io.github.lawrencekcleung.pft.model;

/** Non-negative amount in integer cents. Always formatted at 2dp, no currency symbol. */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);
    private static final Pattern DECIMAL = Pattern.compile("^\\d+(\\.\\d{1,2})?$");

    /** Accepts "42", "42.5", "42.50". Rejects negatives, >2dp, separators, blanks. */
    public static Money parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("amount is required");
        String s = raw.trim();
        if (!DECIMAL.matcher(s).matches()) {
            throw new IllegalArgumentException("invalid amount '" + raw
                + "': expected a non-negative number with at most 2 decimal places");
        }
        int dot = s.indexOf('.');
        String whole = dot < 0 ? s : s.substring(0, dot);
        String frac  = dot < 0 ? "00" : (s.substring(dot + 1) + "00").substring(0, 2);
        try {
            long dollars = Long.parseLong(whole);
            return new Money(Math.addExact(Math.multiplyExact(dollars, 100), Long.parseLong(frac)));
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("amount out of range: " + raw, e);
        }
    }

    public static Money ofCents(long cents)          { return new Money(cents); }
    public Money plus(Money other)                   { return new Money(Math.addExact(cents, other.cents())); }
    public Money minus(Money other)                  { return new Money(Math.subtractExact(cents, other.cents())); }
    public Money negate()                            { return new Money(Math.negateExact(cents)); }
    public boolean isZero()                          { return cents == 0; }
    public boolean isNegative()                      { return cents < 0; }

    /** "42.00"; "-1234.56" when negative (totals only). */
    public String format() {
        long abs = Math.abs(cents);
        String body = String.format(java.util.Locale.ROOT, "%d.%02d", abs / 100, abs % 100);
        return cents < 0 ? "-" + body : body;
    }

    /** "+3157.70" / "-120.00" / "0.00" — used for net. */
    public String formatSigned() { return (cents > 0 ? "+" : "") + format(); }

    @Override public int compareTo(Money other) { return Long.compare(cents, other.cents()); }
    @Override public String toString()          { return format(); }
}
```

Notes:
- `parse` gates input with a regex; arithmetic uses `*Exact`/`+Exact` so overflow throws instead of wrapping.
- Record equality is exactly cents equality — unlike `BigDecimal`, where `1.0` and `1.00` are unequal.
- `.50` is rejected (no leading digit); documented because a hurried user may type it.

| Input | Result |
|---|---|
| `42` | `4200` → `42.00` |
| `42.5` | `4250` → `42.50` |
| `0.00` | `0`, `isZero()` |
| `5000000.00` | `500_000_000` |
| `42.505` | rejected — 3dp |
| `-1.00` | rejected — negative |
| `1,234.50` | rejected — separator |
| `.50` | rejected — no leading digit |
| `""`, `null`, `abc` | rejected |
| `999999999999999999` | rejected — out of range |

### 6.2 `Transaction`

```java
public record Transaction(int id, LocalDate date, TransactionType type,
                          Money amount, String category, String description) {
    public Transaction {
        Objects.requireNonNull(date); Objects.requireNonNull(type); Objects.requireNonNull(amount);
        if (amount.isNegative()) throw new IllegalArgumentException("amount must be non-negative");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
        if (category == null || category.isBlank()) category = Category.UNCATEGORIZED;
    }
    public boolean isExpense() { return type == TransactionType.EXPENSE; }
    public boolean isIncome()  { return type == TransactionType.INCOME; }
}
```

`TransactionType` is `INCOME`/`EXPENSE` with a wire name (`income`/`expense`) for CSV. Category is a `String`; `Category.UNCATEGORIZED = "UNCATEGORIZED"` is the only constant that matters.

### 6.3 Invariants

- `amount` is non-negative and exactly 2dp by construction (`Money`).
- `category` is never blank.
- `id` is positive, unique, strictly increasing within a store, never reused after delete.
- `description` is non-blank and preserved byte-for-byte (trimmed at the edges only).

### 6.4 Aggregation rules

- Income total = sum of `INCOME`, expenses total = sum of `EXPENSE`, both non-negative.
- Net = income − expenses, may be negative.
- Category breakdown covers **expenses only**; income is not a category row.
- Sums use `Collectors.summingLong(tx -> tx.amount().cents())` — exact.
- Percentages use integer basis points: `spend * 10_000 / total`, formatted to 1dp at the edge only.

---

## 7. Persistence

### 7.1 CSV format contract

Default path `./data/transactions.csv`.

```
id,date,type,amount,category,description
1,2026-11-01,income,5000.00,income,Salary Oct
2,2026-11-01,expense,42.50,groceries,"Countdown, Auckland"
```

- Header always written first; UTF-8, LF endings, trailing newline.
- Column order fixed: `id,date,type,amount,category,description`.
- `amount` is canonical 2dp text; `type` is `income`/`expense`.
- `description` is RFC 4180 quoted when it contains `,`, `"`, or a newline; embedded quotes doubled.
- Row order = id order (append order). `findAll()` makes no ordering promise; display sorts explicitly.
- No comments, no blank lines, no BOM.
- Next id lives in a sidecar `transactions.csv.seq` (plain text, one integer) and is written **before** the data file, so a crash can only skip an id, never reuse one. If the sidecar is missing, `max(id)+1` from the data file is the fallback; the sidecar is then recreated on the next write.

### 7.2 Write model

Every mutation: read whole file → mutate in memory → write `transactions.csv.tmp` → `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.

- A crash mid-write leaves the previous file intact; no partial state is ever observable.
- `insert` writes `transactions.csv.seq` (next id) before the data file; a failure between the two leaks an id rather than reusing it, and the data file is untouched.
- `insertAll` writes once, not once per row.
- Read-only commands (`list`, `summary`, `analyze`, `categorize` without `--apply`) never create the file or the `data/` directory.

### 7.3 Failure behaviour

| Situation | Behaviour |
|---|---|
| Corrupt store (bad column count, unparseable amount, duplicate id, bad date) | Abort with `data/transactions.csv:7: expected 6 columns, got 5`; exit `2`; file untouched; no silent repair |
| Missing file / directory | Treated as an empty store; created on first successful write |
| Import row invalid | Skip row, report source line, commit valid rows, exit `2` |
| Unknown id in `delete`/`categorize --id` | Exit `2`, no write |
| Temp file left by a kill | Ignored; overwritten by the next write |

### 7.4 The seam — `TransactionRepository`

```java
package io.github.lawrencekcleung.pft.store;

public interface TransactionRepository extends AutoCloseable {

    /** All transactions, order unspecified — callers sort. */
    List<Transaction> findAll();

    Optional<Transaction> findById(int id);

    /** Persists a new transaction and returns it with an assigned id. */
    Transaction insert(Transaction draft);

    /** Replaces the stored transaction with the same id. */
    void update(Transaction tx);

    /** @throws StorageException if the id is unknown */
    void deleteById(int id);

    /** All-or-nothing. Default loops insert(); adapters may override for one atomic write. */
    default List<Transaction> insertAll(List<Transaction> drafts) {
        List<Transaction> saved = new ArrayList<>(drafts.size());
        for (Transaction draft : drafts) saved.add(insert(draft));
        return List.copyOf(saved);
    }

    /** No-op for file stores; closes the connection for JDBC stores. */
    @Override default void close() { }
}
```

Deliberate properties:

| Property | Why |
|---|---|
| `findAll` order unspecified | CSV returns file order, SQLite would return rowid order; pinning order bakes one adapter's constraint into the contract |
| Ids assigned by the store | CSV uses a sidecar high-water mark, SQLite would use `AUTOINCREMENT`; callers never compute ids |
| `insertAll` default + override | Correct by default, efficient when overridden |
| No `flush`/`commit`/`Path filePath()` | Those leak CSV mechanics into the abstraction |
| `AutoCloseable` | Free today, means a JDBC adapter needs no interface change |
| `StorageException extends RuntimeException` | Services and CLI never see `IOException`/`SQLException`; one place maps to exit `2` |

Factory — the only class that knows about formats:

```java
public final class RepositoryFactory {
    public static TransactionRepository open(Path dataPath) {
        String name = dataPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) return new CsvTransactionRepository(dataPath);
        // future: .db / .sqlite -> new SqliteTransactionRepository(dataPath)
        throw new UsageException("unsupported data file '" + dataPath + "': expected a .csv file");
    }
}
```

Wiring, one line, nothing above it knows the format:

```java
CommandRegistry registry = new CommandRegistry(List.of(/* one definition per command */));
int exitCode = Cli.defaults(registry).run(args);
```

`Cli` resolves the data path (`--data` → `PFT_DATA` → default), opens the adapter through
`RepositoryFactory`, and closes it after the handler returns, so services still never see a
`Path`.

Swapping stores is then **configuration, not code**: `pft --data data/pft.db summary` starts working the day the adapter lands.

### 7.5 Porting map (for the future adapter)

| Concern | CSV (now) | SQLite (future) |
|---|---|---|
| Location | `--data data/transactions.csv` | `--data data/pft.db` |
| Amount | 2dp text `42.50` | `INTEGER` cents `4250` |
| Date | ISO `2026-11-01` | `TEXT` ISO (keeps dumps readable) |
| Id | sidecar high-water mark (`transactions.csv.seq`) | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| Atomicity | temp file + `ATOMIC_MOVE` | `BEGIN` / `COMMIT` |
| `findAll` order | file order | rowid order |
| Escaping | RFC 4180 quoting | prepared statements |
| Aggregation | full scan + streams | optional `GROUP BY` (not required) |

Subtlety to document: a persisted high-water mark and SQLite `AUTOINCREMENT` both guarantee "never reused", whereas plain `max(id)+1` (when the highest row is deleted) and plain SQLite `rowid` may reuse the highest deleted id. The contract test in §12.2 catches it.

### 7.6 Lifecycle walkthrough (used in the README and as a test scenario)

```console
$ pft add --date 2026-11-01 --type income --amount 5000 --description "Salary Oct" --category income
$ pft add --date 2026-11-01 --type expense --amount 42.50 --description "Countdown, Auckland"
$ pft add --date 2026-11-02 --type expense --amount 1200.00 --description "November rent"
$ pft import --file nov.csv          # 4 valid, 1 invalid -> exit 2, ids #4..#7, no gap
$ pft categorize                     # dry run, file byte-identical
$ pft categorize --apply             # 6 rows categorized
$ pft categorize --id 7 --category client-entertainment
$ pft delete --id 3                  # row gone, ids not renumbered
$ pft add ... "Coffee" 9.90          # -> #8 (high-water mark, not count+1)
$ pft add ... "Coffee" 9.90          # -> #9
$ pft analyze                        # flags #8/#9 as duplicate-charge
$ pft summary --month 2026-11
```

Resulting file after the sequence:

```csv
id,date,type,amount,category,description
1,2026-11-01,income,5000.00,income,Salary Oct
2,2026-11-01,expense,42.50,groceries,"Countdown, Auckland"
4,2026-11-02,expense,15.80,transport,Uber to airport
5,2026-11-03,expense,68.40,groceries,Countdown weekly shop
6,2026-11-04,expense,12.00,subscriptions,Spotify
7,2026-11-06,expense,26.10,client-entertainment,"Lunch, Ponsonby"
8,2026-11-06,expense,9.90,dining,Coffee
9,2026-11-07,expense,9.90,dining,Coffee
```

Summary for that file: income `5000.00`, expenses `184.60`, net `+4815.40`, five category rows.

| Command | Writes? |
|---|---|
| `list`, `summary`, `analyze` | No |
| `categorize` (no `--apply`) | No |
| `add`, `import`, `delete`, `categorize --apply`, `categorize --id` | Yes |

---

## 8. CLI framework (extensible arguments)

Options are **declared**, not parsed ad hoc.

```java
public record OptionSpec(String name, String help, OptionType type,
                         boolean required, String defaultValue) {}

public enum OptionType {
    STRING, FLAG, DATE, AMOUNT, YEAR_MONTH, INT, PATH;

    private static final Map<OptionType, Function<String, Object>> CONVERTERS = Map.of(
        STRING,     s -> s,
        FLAG,       s -> true,
        DATE,       s -> LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE),
        AMOUNT,     Money::parse,
        YEAR_MONTH, YearMonth::parse,
        INT,        Integer::parseInt,
        PATH,       Path::of);
}

public record CommandDefinition(String name, String summary,
                                List<OptionSpec> options, CommandHandler handler) {
    public List<OptionSpec> allOptions();   // command options + globals (--data, --help)
}

@FunctionalInterface
public interface CommandHandler {
    void run(ParsedArgs args, AppContext ctx) throws UsageException;
}

public final class ParsedArgs {
    public String requireString(String name);
    public Optional<String> optionalString(String name);
    public Money requireAmount(String name);
    public LocalDate requireDate(String name);
    public YearMonth optionalMonth(String name);
    public int requireInt(String name);
    public OptionalInt optionalInt(String name);
    public Optional<Path> optionalPath(String name);   // for the global --data
    public boolean flag(String name);
}
```

Behaviour:

- `ArgumentParser` supports `--name value` and `--name=value`, validates required options, and converts **eagerly**. Conversion failures become `UsageException("--date: expected yyyy-MM-dd")` — one consistent error shape.
- `CommandRegistry` is a `Map<String, CommandDefinition>`; `Main` contains no `switch` on command names.
- `HelpPrinter` renders root and per-command help from the specs, so docs cannot drift.
- Globals (`--data`, `--help`) are appended to every command definition in one place.
- Parsing is deliberately **imperative** (index-based token scan) — streams buy nothing there. This is called out so a reviewer doesn't read it as inconsistency.

### 8.1 Recipe — add an option

1. Add one line to the command definition: `new OptionSpec("account", "Account label", STRING, false, "default")`.
2. Read it: `args.optionalString("account")`.
3. Add one parser test. Help text updates automatically.

### 8.2 Recipe — add a command

1. Implement `CommandHandler` in `cli/commands/`.
2. Add one `CommandDefinition` to the registry.
3. Nothing else changes: dispatch, help, and exit-code mapping are already generic.

---

## 9. Rule framework (extensible analysis)

### 9.1 Interfaces

```java
public sealed interface Rule permits CategorizationRule, AnalysisRule {
    String id();            // stable, unique, used by --rule
    String description();
}

public non-sealed interface CategorizationRule extends Rule {
    Optional<String> classify(Transaction tx);
}

public non-sealed interface AnalysisRule extends Rule {
    List<Finding> evaluate(AnalysisContext ctx);
}

public record AnalysisContext(List<Transaction> transactions, YearMonth period) {
    public List<Transaction> expenses();
}

public record Finding(String ruleId, List<Integer> transactionIds, String message) {}

public final class FindingPrinter {
    public String format(Finding finding);   // "[duplicate-charge] #8, #9  message"
}
```

```java
public final class RuleRegistry {
    private final List<CategorizationRule> categorizationRules;  // ordered, first match wins
    private final List<AnalysisRule> analysisRules;

    public static RuleRegistry defaults();
    public static RuleRegistry of(List<CategorizationRule> c, List<AnalysisRule> a);  // tests
    public List<CategorizationRule> categorizationRules();
    public List<AnalysisRule> analysisRules();
    public Optional<AnalysisRule> analysisRule(String id);
}
```

- Registration is explicit and ordered — no `ServiceLoader`, no reflection, no config file.
- Tests inject fakes through `RuleRegistry.of(...)`.
- `RuleRegistryTest` asserts **unique non-blank ids** and non-empty descriptions, guarding against collisions as rules accumulate.
- `FindingPrinter` is the only place that formats findings, so new rules render identically.

### 9.2 Categorization rules (implemented)

`KeywordCategorizationRule` — case-insensitive substring match on description, first rule wins:

| Category | Keywords (any match) |
|---|---|
| `groceries` | countdown, woolworths, new world, paknsave |
| `rent` | rent, landlord |
| `transport` | uber, mrt, cityhop, petrol, caltex, z energy |
| `subscriptions` | netflix, spotify, icloud |
| `dining` | cafe, coffee, restaurant, lunch |
| `health` | pharmacy, chemist, doctor, dentist |
| `utilities` | electricity, water, internet, broadband |

Rules apply only to `UNCATEGORIZED` rows; manual assignments are never overwritten; re-running `--apply` is a no-op.

### 9.3 Analysis rule (implemented): `duplicate-charge`

Flags a pair of **expenses** where:

- normalized descriptions match (trim, lowercase, collapse internal whitespace),
- `Money` is exactly equal (cents equality),
- date difference is ≤ 3 days,
- they are distinct transactions,
- neither is income.

Output pairs sorted by earliest date then ids; each transaction may appear in more than one pair (documented, keeps the rule simple). Window size is a constructor parameter (`new DuplicateChargeRule(3)`), defaulted in `RuleRegistry`.

Why this rule: it is the kind of thing a personal tracker actually surfaces, it is deterministic, it has interesting boundaries (day 3 vs day 4, differing amounts, income, same-day), and it cannot be mistaken for the brief's `$50` example.

### 9.4 Recipe — add an analysis rule

1. Implement `AnalysisRule`; return `Finding`s referencing real transaction ids.
2. Add it to `RuleRegistry.defaults()`.
3. Add a test class extending `AnalysisRuleContractTest` (id present, deterministic output, ids exist, income handling).
4. `analyze --rule <new-id>` works with no CLI change.

---

## 10. Build, run, and deliverable hygiene

```
build.gradle.kts        # java + application, toolchain 21, JUnit 5
settings.gradle.kts
.gitignore              # build/, .gradle/, data/, *.db
README.md
PRD.md
```

- `gradle test` — unit + contract + integration tests.
- `gradle build` — compiles and produces `build/libs/pft.jar` with a main-class manifest.
- `gradle installDist` — runnable launcher at `build/install/pft/bin/pft` (generated, not committed).
- `gradle run --args "summary --month 2026-11"` for ad-hoc use.
- **No `gradlew` committed** (it bundles a jar). README states Gradle 8.5+ is required and shows `gradle wrapper` for anyone who wants a local wrapper.
- No binaries, no data files, no IDE metadata committed. Nothing under `build/` is delivered.

---

## 11. Services

| Service | Responsibility |
|---|---|
| `TransactionService` | `add`, `import`, `list` filtering, `delete`; owns validation order and id assignment via the repository |
| `CategorizationService` | Builds the dry-run diff, applies it, handles manual overrides; knows nothing about keywords beyond calling `CategorizationRule` |
| `SummaryService` | Totals, net, category breakdown, percentage formatting; `Clock`-free (period comes from `--month`, default all-time) |
| `RuleRegistry` | Rule composition and lookup (not a service, but the only rule entry point) |

Services never write to stdout. They return values or throw; the CLI layer formats. This keeps summary/table formatting testable without capturing output.

Exceptions:

- `UsageException` — bad invocation → exit `1`.
- `DataException` — invalid data or unknown id → exit `2`.
- `StorageException` — corrupt/unwritable store → exit `2`.
- Only `Main` maps exceptions to codes; no `System.exit` deeper in the stack.

---

## 12. Testing strategy

Target ~30–35 tests, all fast, no network, no shared state.

### 12.1 Unit tests

| Area | Cases |
|---|---|
| `Money` | the §6.1 table; `plus`/`minus` including negative net; `format`/`formatSigned`; overflow throws |
| `Transaction` | blank description, negative amount, category defaulting |
| CSV codec | comma/quote/newline/Unicode round-trip; header detection; corrupt line reporting with line number; temp file cleanup |
| `ArgumentParser` | `--k=v`, separate value, missing required, unknown option, bad type conversions, generated help |
| Keyword rules | first-match wins, case-insensitivity, already-categorized untouched |
| Summary | totals, net sign, grouping order, empty month, zero expenses (no divide-by-zero) |
| `DuplicateChargeRule` | day 3 vs day 4 boundary, same amount different description, income excluded, same-day pair, multiple pairs |

### 12.2 Repository contract suite (the seam guarantee)

`TransactionRepositoryContractTest` — abstract, one subclass per adapter:

```
emptyStoreReturnsNoTransactions
insertAssignsStrictlyIncreasingIds
findByIdMissReturnsEmpty
updateReplacesStoredValues
deleteRemovesAndNeverReusesId          // delete 3, insert -> id != 3
insertAllIsAllOrNothing                // a null draft -> nothing persisted
insertAllPersistsEveryRow              // increasing ids, input order, one write
roundTripsZeroAndFiveMillion
roundTripsCommaQuoteAndUnicodeInDescription
roundTripsUncategorizedValue
unknownUpdateAndDeleteAreStorageExceptions
```

`CsvTransactionRepositoryContractTest` supplies a temp-directory factory; `InMemoryTransactionRepositoryContractTest` runs the same suite against the test fixture. A future SQLite adapter is "done" when the same base goes green. `SqliteTransactionRepositoryContractTest` would add migrations/pragmas on top.

### 12.3 Integration tests

- Drive `Cli` in-process with args against a temp data file; assert stdout and exit code (not `System.exit`).
- `InMemoryTransactionRepository` (test fixture, ~30 lines) exercises the CLI without touching disk — a third implementation, which is what makes the abstraction credible.
- Golden-file test for the §7.6 sequence: run the commands, assert the final CSV text and the summary output.
- Factory test: `.csv` → CSV adapter; `.db` → `UsageException` today (changes by one line when SQLite lands).
- Determinism: every test that collects results asserts ordering; no parallel streams anywhere.

---

## 13. Java style — streams policy

Use streams for collection transformation; keep imperative code where it is clearer.

**Used:**

| Area | Application |
|---|---|
| CSV read | `Files.lines(...)` in try-with-resources, `.map(parser::parse).toList()` |
| `list` | `filter` (month/category/uncategorized) → `sorted(comparing(date).thenComparing(id))` → `toList()` |
| `categorize` | `filter(isUncategorized)` → `map(rule)`; `Optional` chaining |
| `summary` | `Collectors.partitioningBy(isIncome)`; `groupingBy(category, TreeMap::new, collectingAndThen(summingLong, Money::ofCents))` |
| `analyze` | `stream().groupingBy(...)`, `IntStream.range` for window comparisons, `flatMap` to findings |
| `RuleRegistry` | `filter(id::equals).findFirst().orElseThrow()`; duplicate-id assertion via `groupingBy(counting)` |
| `import` | `IntStream.range(0, lines.size())` so each row keeps its source line number |
| Formatting | `Collectors.joining`, `String.format(Locale.ROOT, ...)` |

**Deliberately not used:**

- CSV tokenizer — quote-aware state machine.
- `ArgumentParser` — index-based token scan.
- Command dispatch — `Map` lookup.
- File writes — `BufferedWriter` loop inside the atomic write.
- Anywhere a checked exception would need a sneaky-throws wrapper.

Conventions: `.toList()` over `Collectors.toList()`; no parallel streams; ordered collectors only (`TreeMap`, `LinkedHashMap`, explicit `sorted`), so tests never flake.

---

## 14. Task breakdown and order

Every task ends with a green `gradle test` and something demonstrable. Task boundaries are commit boundaries.

| # | Task | Acceptance | Est. |
|---|---|---|---|
| 0 | Gradle scaffold — KTS build, toolchain 21, JUnit 5, `application` plugin, manifest, `.gitignore`, `git init` | `gradle test` and `gradle installDist` succeed | 20m |
| 1 | Domain — `Money`, `Transaction`, `TransactionType`, validation + tests | §6.1 table passes; invalid input rejected with clear messages | 30m |
| 2 | Repository seam + CSV adapter — interface, `StorageException`, factory, contract base class, CSV impl, CSV-specific tests | Contract suite green; comma/quote/Unicode round-trip; atomic write | 55m |
| 3 | CLI framework — `OptionSpec`, `OptionType`, `ParsedArgs`, `ArgumentParser`, `CommandDefinition`, `CommandRegistry`, `HelpPrinter`, exceptions | Parser and help tests; unknown option and bad type produce `--name: ...` messages | 60m |
| 4 | `add` + `list` through the framework | End-to-end against a temp data file | 40m |
| 5 | `import` — header detection, per-line errors, skip-bad-rows, single write | 10-row fixture imports; exit `2` on one bad row; no id gap | 30m |
| 6 | Rule framework — interfaces, `Finding`, `AnalysisContext`, `RuleRegistry`, contract base + uniqueness test | Framework green with one stub rule | 30m |
| 7 | Categorization — `KeywordCategorizationRule`, dry-run vs `--apply`, manual override | Re-running `--apply` is a no-op and writes nothing | 40m |
| 8 | `summary` — totals, net, category table, `--month`, integer percentages | Matches hand-computed fixture; no divide-by-zero on empty month | 40m |
| 9 | `DuplicateChargeRule` + `analyze --rule` | Boundary tests at 3 vs 4 days; income excluded; deterministic order | 30m |
| 10 | Hardening — `delete`, `--uncategorized`, CLI integration tests, `InMemoryTransactionRepository`, factory test, corrupt-store behaviour | ~30–35 tests green | 45m |
| 11 | README — run steps, sample transcript, assumptions, §8/§9/§7 recipes, porting map | Reviewer runs it in two commands | 35m |

**Total: 7h35m of human effort at the top of the range.**

Notes on scope:

- The figure includes tests and documentation. The functional path (tasks 0–9 + 11) is ~6h50m; a minimal "works and is documented" cut is ~5h.
- The two frameworks cost ~90m up front and make tasks 4–10 nearly mechanical — that is the maintainability story to tell in the interview.
- Earlier drafts quoted 5h45m and 6h10m; both predated the CLI framework, the rule framework task, and the persistence seam, and the later arithmetic slipped. The table above is authoritative.
- If time-boxed: drop task 10's `delete`/`--uncategorized`/`InMemoryTransactionRepository` extras first, then trim the README recipes. Do **not** drop the seam, the contract test, or the analysis rule.

Critical path: `0 → 1 → 2 → 3`. After task 3, tasks `4 → 5 / 8` and `6 → 7 / 9` can proceed in parallel.

---

## 15. Future evolution

| Change | Cost | How |
|---|---|---|
| SQLite storage | ~2.5h | New `SqliteTransactionRepository` + migration runner; extend `RepositoryFactory`; add subclass of the contract test. Services, CLI, rules unchanged |
| New analysis rule | ~30m | Class + one registry line + one contract-test subclass |
| New categorization rule | ~15m | One entry in the keyword ordered map |
| Rule parameters without rebuild | ~1h | `rules.properties` read in `RuleRegistry.defaults()` — deliberately deferred |
| Editing an amount | ~30m | `update` already exists on the repository; add an `edit` command |
| Budget alerts | ~1h | New `AnalysisRule` comparing month-to-date spend to a configured budget |
| Export | ~20m | `export --file out.csv` reusing the CSV codec |

Rejected for now, stated in the README: `ServiceLoader` rule discovery (indirection without benefit at this size), reflection-based command dispatch (breaks IDE navigation), config files for keyword rules (a code review is a better place to change two keywords).

---

## 16. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Scope creep past "a few hours" | Missed deadline, over-engineered submission | Fixed task table, commit per task, §14 trim order documented |
| "No binaries" vs. Gradle wrapper | Reviewer can't build | Ship no wrapper; README states Gradle 8.5+ and shows `gradle wrapper` |
| Reviewer can't see the data model intuitively | Harder interview | CSV is diffable; §7.6 transcript shows the file evolving |
| Rule seen as derivative of the brief's example | Loses the credit for the optional part | `duplicate-charge` is unrelated to a `$50` threshold and has real edge cases |
| Framework code reads as over-engineering | Credibility loss on "keep it simple" | Frameworks are ~250–300 lines total, each deletion-testable, each with a one-line recipe; no reflection, no config, no DI container |
| Full-file rewrite at scale | Slower with tens of thousands of rows | Explicitly out of scope (§4), with SQLite as the documented escape hatch |
| Hidden nondeterminism in aggregation | Flaky tests, wrong output | TreeMap/ordered collectors, ascending sorts, no parallel streams |

---

## 17. README outline (deliverable)

1. What it is, one paragraph, and the assumptions list from §4.
2. Build and run: Gradle requirement, `gradle installDist`, `gradle test`, `gradle run`.
3. Five-minute tour: the §7.6 transcript with the file shown at the interesting steps.
4. Commands reference with exit codes.
5. Design notes: why `long` cents, why CSV now, why the repository seam, why these two frameworks and not more.
6. How to extend: add an option (§8.1), add a command (§8.2), add an analysis rule (§9.4), add a persistence adapter (§7.5).
7. Known limitations, straight from §4 and §16.

---

## 18. Definition of done

- `gradle build` and `gradle test` pass from a clean checkout with only JDK 21 and Gradle installed.
- The §7.6 transcript runs exactly as shown, including exit codes.
- The CSV file matches the §7.1 contract byte-for-byte after each command.
- All three sample runs behave as documented: `categorize` idempotent, `analyze` non-persistent, `summary` matching a hand-checked total.
- No binaries, no committed data, no IDE metadata.
- README answers "why" for every non-obvious choice in §2.
