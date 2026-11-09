# pft — personal finance tracker

`pft` is a small command-line tracker for one person's income and
expenses. It keeps everything in a plain CSV file, imports bank-style exports with
per-row validation, categorizes transactions on demand from an ordered keyword table,
prints monthly spend summaries, and flags likely duplicate charges with a rule-based
analyzer.

The full design is in [`PRD.md`](PRD.md); [`tasks.md`](tasks.md) records the build order
and the verification for each step.

## Assumptions

1. **Single user, single currency, single process.** There is no locking beyond atomic
   file replacement; two simultaneous invocations may lose one write.
2. **No currency symbol.** Amounts print as bare two-decimal text (`1234.50`). More than
   two decimal places is rejected, never rounded silently.
3. **ISO dates.** `yyyy-MM-dd`, no timezone; "month" means the calendar month.
4. **The data file is local.** Resolution order: `--data <path>` → `PFT_DATA` →
   `./data/transactions.csv`.
5. **Import does not deduplicate.** Importing the same file twice creates duplicates,
   which `analyze` will then flag.
6. **Ids are opaque, monotonic and never reused.** They are not row numbers.
7. **Analysis is advisory and read-only.** Findings are recomputed every run.
8. **Categorization rules live in code.** There is no rules config file; changing a
   keyword is a code review, not an ops task.
9. **Personal scale.** Full-file rewrite per mutation is fine for hundreds to low
   thousands of transactions.
10. **Amount magnitude.** Realistic amounts are far inside `long`; overflow throws rather
    than wrapping.
11. **Out of scope:** accounts, budgets, editing an amount (delete and re-add), multiple
    currencies, exports beyond CSV, i18n.

## Build and run

Java 21 and Gradle 8.5+ are required (the build is tested with Gradle 9.2.1). No Gradle
wrapper is committed because it bundles a binary jar; run `gradle wrapper` locally if
you want one.

```console
gradle test
gradle installDist
build/install/pft/bin/pft --help
```

`installDist` is the runnable artifact: it bundles picocli. The plain jar under
`build/libs/` has a main-class manifest but expects picocli on the classpath.

Add `build/install/pft/bin` to `PATH` (or alias the binary) to use `pft` below.

The only runtime dependency is [picocli](https://picocli.info/) 4.7.7, used for
parsing, generated help, subcommand dispatch and typo suggestions. The domain,
storage and rule code have no dependencies.
`gradle -q run --args="summary --month 2026-11"` works too.

The data file is created on the first write. `list`, `summary`, `analyze` and
`categorize` without `--apply` never create it. Only `.csv` is accepted today; other
extensions fail with a usage error.

Project layout:

```
src/main/java/io/github/lawrencekcleung/pft/
  model/    Money, Transaction, TransactionType, Category
  store/    TransactionRepository, CSV codec/repository, factory
  service/  add/list/import/categorize/summary use cases
  rules/    rule model, registry, keyword and duplicate-charge rules
  cli/      picocli root/commands, converters, handlers, formatters
  error/    UsageException, DataException, StorageException
```

## Five-minute tour

Run from a directory where you want `data/transactions.csv` to live.

```console
$ pft add --date 2026-11-01 --type income --amount 5000 --description "Salary Oct" --category income
Added #1 (category: income)

$ pft add --date 2026-11-01 --type expense --amount 42.50 --description "Countdown, Auckland"
Added #2 (category: UNCATEGORIZED)

$ pft add --date 2026-11-02 --type expense --amount 1200.00 --description "November rent"
Added #3 (category: UNCATEGORIZED)
```

`nov.csv` contains four good rows and one bad amount:

```csv
date,type,amount,description
2026-11-02,expense,15.80,Uber to airport
2026-11-03,expense,68.40,Countdown weekly shop
2026-11-04,expense,12.00,Spotify
2026-11-06,expense,26.10,"Lunch, Ponsonby"
2026-11-07,expense,abc,Broken
```

```console
$ pft import --file nov.csv
Imported 4 transactions.
nov.csv:6: invalid amount 'abc': expected a non-negative number with at most 2 decimal places
Skipped 1 row.

$ echo $?
2
```

Valid rows were committed with ids #4–#7; the skipped row consumed no id. Review the
keyword rules before writing:

```console
$ pft categorize
#2  UNCATEGORIZED -> groceries  "Countdown, Auckland"
#3  UNCATEGORIZED -> rent  "November rent"
#4  UNCATEGORIZED -> transport  "Uber to airport"
#5  UNCATEGORIZED -> groceries  "Countdown weekly shop"
#6  UNCATEGORIZED -> subscriptions  "Spotify"
#7  UNCATEGORIZED -> dining  "Lunch, Ponsonby"
Dry run: 6 transactions would be categorized. Use --apply to write.
```

The file is byte-identical after the dry run. Apply, then override one row manually:

```console
$ pft categorize --apply
#2  UNCATEGORIZED -> groceries  "Countdown, Auckland"
#3  UNCATEGORIZED -> rent  "November rent"
#4  UNCATEGORIZED -> transport  "Uber to airport"
#5  UNCATEGORIZED -> groceries  "Countdown weekly shop"
#6  UNCATEGORIZED -> subscriptions  "Spotify"
#7  UNCATEGORIZED -> dining  "Lunch, Ponsonby"
Categorized 6 transactions.

$ pft categorize --id 7 --category client-entertainment
Categorized #7 as client-entertainment.
```

Delete a row (ids are not renumbered), then add two identical charges:

```console
$ pft delete --id 3
Deleted #3.

$ pft add --date 2026-11-06 --type expense --amount 9.90 --description Coffee --category dining
Added #8 (category: dining)

$ pft add --date 2026-11-07 --type expense --amount 9.90 --description Coffee --category dining
Added #9 (category: dining)

$ pft analyze
[duplicate-charge] #8, #9  9.90 "Coffee" on 2026-11-06 and 2026-11-07 (1 day apart)

$ pft summary --month 2026-11
Period: 2026-11   Income: 5000.00   Expenses: 184.60   Net: +4815.40

Category                     Spend     Share
groceries                   110.90     60.1%
client-entertainment         26.10     14.1%
dining                       19.80     10.7%
transport                    15.80      8.6%
subscriptions                12.00      6.5%
```

The resulting file:

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

## Commands

| Command | Options | Notes |
|---|---|---|
| `add` | `--date`, `--type income\|expense`, `--amount`, `--description`, `[--category]` | Prints `Added #id` and the effective category |
| `import` | `--file <csv>` | `date,type,amount,description[,category]`; header optional; leading `id` ignored; bad rows skipped and reported |
| `list` | `[--month yyyy-MM]`, `[--category]`, `[--uncategorized]` | Date-then-id order; prints `No transactions.` when empty |
| `categorize` | `[--apply]`, `[--id N --category X]` | Dry run unless `--apply`; `--id` writes immediately |
| `summary` | `[--month yyyy-MM]` | Totals, signed net, expense breakdown; all time by default |
| `analyze` | `[--month yyyy-MM]`, `[--rule id]` | Read-only; prints `No findings.` when clean |
| `delete` | `--id N` | Removes one row; does not renumber; ids are never reused |

Global options: `--data <path>` (may appear before or after the command) and `--help`.
`pft <command> --help` prints generated help.

| Exit code | Meaning |
|---|---|
| `0` | Success |
| `1` | Usage error: bad flags, missing required option, unknown command or rule |
| `2` | Data error: invalid value, unknown id, corrupt store, partial import |

Errors go to stderr; normal output goes to stdout. No command calls `System.exit`.

## Design notes

**Money is integer cents.** `Money` wraps a `long`, so arithmetic and grouping are exact.
No `BigDecimal`, no `double`. Overflows throw instead of wrapping.

**CSV now, behind a seam.** Services depend on `TransactionRepository`
(`findAll`/`findById`/`insert`/`update`/`deleteById`/`insertAll`) and never see a path.
`RepositoryFactory` is the only class that knows the format. A SQLite adapter is a new
class plus one factory line; the same contract suite then proves it behaves identically.

**Ids are never reused.** `insert` reads the data file's highest id and a sidecar
`transactions.csv.seq` high-water mark, then writes the sidecar *before* the data file.
A crash between the two writes can skip an id but never hand it out twice. Deleting the
highest row therefore cannot make the next insert reuse it.

**Writes are atomic.** Every mutation rewrites through `transactions.csv.tmp` and an
`ATOMIC_MOVE`, so a kill mid-write leaves the previous file intact. `insertAll` writes
once, which is why a partial import commits all valid rows in one step.

**Small libraries over hand-rolled infrastructure.** Picocli owns argument parsing,
generated help, subcommand dispatch, `-h`/`--version` and typo suggestions; the
[Command Line Interface Guidelines](https://clig.dev/) recommend using a parsing library,
and this removed roughly 800 lines of custom parser/help code. The rule framework stays
explicit in `RuleRegistry` — no reflection, service loader, config file or DI container.
Both extension paths are documented by recipes below.

**Error mapping lives in one place.** Commands throw `UsageException` (1),
`DataException`/`StorageException` (2); `Cli` maps types to exit codes and `Main` calls
`System.exit` exactly once.

**Categorization semantics.** Keyword rules match case-insensitive substrings in
description, in declared order, first match wins. Only `UNCATEGORIZED` rows are eligible;
manual overrides are final, so re-running `--apply` is a true no-op.

**Duplicate charges.** `duplicate-charge` pairs distinct expenses whose descriptions match
after trim/lower-case/whitespace collapse, whose cents are equal, and whose dates are at
most three days apart. A transaction may appear in more than one pair; pairs are ordered
by earliest date then ids.

## Extending

### Add an option

1. Add one annotated field to the command class:
   `@Option(names = "--account", description = "Account label") String account;`
2. Read the field in `call()`.
3. Add one command test. Help updates automatically.

### Add a command

1. Implement a `Callable<Integer>` annotated with `@Command` in `cli/commands/`.
2. Add the class to `RootCommand`'s `subcommands`.
3. Return `0` on success, throw the appropriate domain exception on failure, and add an
   integration test. Dispatch, help and exit-code mapping are already generic.

### Add an analysis rule

1. Implement `AnalysisRule` and return `Finding`s referencing real ids.
2. Register it in `RuleRegistry.defaults()`.
3. Extend `AnalysisRuleContractTest` in a test subclass; `analyze --rule <id>` works with
   no CLI change.

### Add a persistence adapter

1. Implement `TransactionRepository`, mapping all failures to `StorageException`.
2. Add the extension to `RepositoryFactory.open`.
3. Extend `TransactionRepositoryContractTest` with a factory for the new adapter. The
   suite already covers id monotonicity, all-or-nothing batches, round-trips and unknown
   ids.

Porting map for SQLite:

| Concern | CSV (now) | SQLite (future) |
|---|---|---|
| Location | `--data data/transactions.csv` | `--data data/pft.db` |
| Amount | 2dp text `42.50` | `INTEGER` cents `4250` |
| Date | ISO `2026-11-01` | `TEXT` ISO |
| Id | sidecar high-water mark | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| Atomicity | temp file + `ATOMIC_MOVE` | `BEGIN` / `COMMIT` |
| `findAll` order | file order | rowid order |
| Escaping | RFC 4180 quoting | prepared statements |

## Testing

`gradle test` runs 308 fast tests with no network or shared state:

- domain and money parsing, including the two-decimal and overflow rules;
- codec round-trips for comma, quote, newline, Unicode, CRLF and BOM;
- the repository contract suite against both the CSV adapter and an in-memory fixture;
- CLI tests driven in-process with captured stdout/stderr and asserted exit codes;
- a golden lifecycle test that replays the tour above and asserts the final CSV and
  summary byte for byte.

`gradle clean build` additionally produces the jar (`build/libs/`) and the distribution.

## Known limitations

- Single process only; concurrent invocations can lose a write.
- Full-file rewrite on every mutation; fine for personal scale, not for bulk data.
- Import is line-oriented: quoted fields may contain commas, but not embedded newlines.
- Import does not deduplicate; `duplicate-charge` will surface the repeats.
- `duplicate-charge` reports pairs, so one transaction can appear in several findings.
- Summary shares are rounded to 1dp and may not total exactly `100.0%`.
- If `transactions.csv.seq` is deleted, the next id falls back to `max(id)+1`, so a
  hand-edited store can reuse the highest deleted id. Starting from a consistent sidecar
  (or an untouched store) preserves the guarantee.
- Categorization applies to every uncategorized row, including income rows, if a keyword
  matches; the shipped keywords target expenses.
- No amount editing (delete and re-add), no budgets, accounts, multi-currency or export.
