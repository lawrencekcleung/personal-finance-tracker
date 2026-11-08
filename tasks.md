# tasks.md — Implementation checklist for `pft`

Working checklist derived from `PRD.md` §14. 50 atomic tasks across 12 phases.
Tick a task only when its verification passes on its own commit.

**Progress: 0 / 50**

---

## Atomicity contract

Every task must satisfy all five, or it isn't done:

1. One commit, message `type(scope): summary` (e.g. `feat(money): parse amounts with 2dp validation`).
2. `gradle test` green on that commit — no "fixed by the next task" failures.
3. No forward references: only types that already exist.
4. `main` never left in a state where a documented PRD command misbehaves.
5. If a task passes ~25 minutes, it was too big — split it rather than push through.

Split candidates if one overruns: **C4** (read/write paths), **D3** (tokenizer vs validation), **I3** (formatter vs golden strings).

---

## Phase A — Scaffold · PRD task 0 · 20m · cumulative 0h20

- [ ] **A1** — Repo cloned from GitHub (public, empty); origin repointed to the `github.com-lawrencekcleung` SSH alias; `.gitignore`, `.gitattributes`, README stub; `PRD.md` and `tasks.md` moved in
      · verify: one commit, 5 tracked text files, no binaries, author `Lawrence Leung <lawrencekcleung@gmail.com>` · dep: — · 10m
- [ ] **A2** — `settings.gradle.kts`, `build.gradle.kts` (java + application, toolchain 21, JUnit 5 config), `Main` usage stub, jar main-class manifest
      · verify: `gradle build`, `gradle installDist`, `build/install/pft/bin/pft` runs · dep: A1 · 10m

_JUnit is configured here with zero tests; the first real test is B1. A zero-test `gradle test` still passes, so the commit is green._

---

## Phase B — Domain · PRD task 1 · 40m · cumulative 1h00

- [ ] **B1** — `Money` record: `ZERO`, `ofCents`, `plus`, `minus`, `negate`, `isZero`, `isNegative`, `compareTo`, `format`, `formatSigned`
      · verify: `MoneyTest` — formatting incl. `-1234.56`, arithmetic, negative net · dep: A2 · 10m
- [ ] **B2** — `Money.parse`: regex gate, 2dp, overflow via `*Exact`
      · verify: `MoneyParseTest` — all 10 rows of PRD §6.1 table · dep: B1 · 10m
- [ ] **B3** — Exceptions: `UsageException`, `DataException`, `StorageException`
      · verify: compiles, thrown nowhere yet · dep: B1 · 5m
- [ ] **B4** — `TransactionType` (+ wire name), `Category.UNCATEGORIZED`, `Transaction` record with compact-constructor validation
      · verify: `TransactionTest` — blank description, negative amount, category defaulting · dep: B2, B3 · 15m

---

## Phase C — Persistence seam + CSV · PRD task 2 · 70m · cumulative 2h10

- [ ] **C1** — `TransactionRepository` interface (`findAll`, `findById`, `insert`, `update`, `deleteById`, default `insertAll`, default `close`)
      · verify: compiles, no implementation yet · dep: B4 · 5m
- [ ] **C2** — `CsvCodec`: header, row parse/serialize, RFC 4180 quoting/escaping, line-numbered errors
      · verify: `CsvCodecTest` — comma, quote, newline, Unicode, header detection, `:7:` corrupt-line message · dep: C1 · 15m
- [ ] **C3** — `CsvTransactionRepository` read path (`findAll`, `findById`), missing file → empty
      · verify: `CsvReadTest` — absent file/dir, empty file, 2 rows · dep: C2 · 10m
- [ ] **C4** — Write path: `insert` (id = max+1), `update`, `deleteById`, temp file + `ATOMIC_MOVE`
      · verify: `CsvWriteTest` — id assignment after delete, unknown-id delete, file contents after each op · dep: C3 · 15m
- [ ] **C5** — `insertAll` override: one write, all-or-nothing
      · verify: `CsvInsertAllTest` — one bad row → nothing persisted · dep: C4 · 5m
- [ ] **C6** — `TransactionRepositoryContractTest` abstract + `CsvTransactionRepositoryContractTest`
      · verify: all 9 contract cases of PRD §12.2 green · dep: C4, C5 · 10m
- [ ] **C7** — `InMemoryTransactionRepository` (test fixture) + contract subclass + write counter
      · verify: contract suite passes; counter available to later no-write assertions · dep: C6 · 5m
- [ ] **C8** — `RepositoryFactory`: `.csv` → adapter, `.db` → `UsageException`
      · verify: `RepositoryFactoryTest` — 2 cases · dep: C4, B3 · 5m

_C7 is intentionally earlier than PRD §14's task 10: every service test needs a fake, and pulling it forward keeps phases E–J disk-free._

---

## Phase D — CLI framework · PRD task 3 · 65m · cumulative 3h15

- [ ] **D1** — `OptionType` enum + converters (`STRING`, `FLAG`, `DATE`, `AMOUNT`→`Money`, `YEAR_MONTH`, `INT`, `PATH`)
      · verify: `OptionTypeTest` — each converter + failure message · dep: B2 · 10m
- [ ] **D2** — `OptionSpec` record + invariant checks (required/default conflict, FLAG takes no value)
      · verify: `OptionSpecTest` — 3 cases · dep: D1 · 5m
- [ ] **D3** — `ArgumentParser`: `--k=v`, `--k v`, unknown option, missing required, eager conversion
      · verify: `ArgumentParserTest` — 8 cases incl. `--date: expected yyyy-MM-dd` · dep: D1, D2 · 15m
- [ ] **D4** — `ParsedArgs` typed accessors (`requireString`, `optionalString`, `requireAmount`, `requireDate`, `optionalMonth`, `flag`)
      · verify: `ParsedArgsTest` — present/absent/wrong type · dep: D3 · 10m
- [ ] **D5** — `CommandDefinition` + `CommandRegistry` (map, duplicate-name rejection)
      · verify: `CommandRegistryTest` — dispatch lookup, duplicate throws · dep: D4 · 5m
- [ ] **D6** — `HelpPrinter`: root help + per-command help generated from specs
      · verify: `HelpPrinterTest` — golden strings, every option appears · dep: D5 · 10m
- [ ] **D7** — `AppContext`, `Cli` facade returning `int`, `Main` (single `System.exit`), exception → exit-code mapping
      · verify: `CliTest` — `--help` → 0, unknown command → 1, `DataException` → 2 · dep: D5, D6, C8 · 10m

> **Milestone 1 — after D7:** framework in place, tool launchable and self-documenting, no commands yet. Internal only.

---

## Phase E — `add` + `list` · PRD task 4 · 40m · cumulative 3h55

- [ ] **E1** — `TransactionService.add` (validation order, repository insert)
      · verify: `TransactionServiceTest` on in-memory repo — happy path, invalid amount/date · dep: D7, C7 · 15m
- [ ] **E2** — `AddCommand` (specs + handler) + registry entry
      · verify: `CliTest` — `add` writes one row to temp CSV, bad flags → exit 1 · dep: E1 · 10m
- [ ] **E3** — `TablePrinter` + `ListCommand` (`--month`, `--category`), sort date→id
      · verify: golden table test, empty-result message · dep: E2 · 15m

> **Milestone 2 — after E3:** demoable MVP. `add`/`list` against a real file with real exit codes. Minimum credible submission.

---

## Phase F — Import · PRD task 5 · 35m · cumulative 4h30

- [ ] **F1** — `ImportParser`: header detection (first field parses as date), row → draft, per-line errors, ids ignored
      · verify: `ImportParserTest` — with/without header, 5 malformed cases · dep: C2 · 15m
- [ ] **F2** — `ImportService`: partial success, `ImportReport` (imported count, skipped rows), single `insertAll`
      · verify: `ImportServiceTest` on in-memory repo — no id gap after skipped row · dep: F1, E1 · 10m
- [ ] **F3** — `ImportCommand` + report printing
      · verify: `CliTest` — 4 valid + 1 bad → exit 2, file has 4 rows, ids 4–7 · dep: F2 · 10m

---

## Phase G — Rule framework · PRD task 6 · 25m · cumulative 4h55

- [ ] **G1** — `Rule` (sealed), `CategorizationRule`, `AnalysisRule`, `Finding`, `AnalysisContext` (+ `expenses()`)
      · verify: `AnalysisContextTest` — filtering, period · dep: B4 · 5m
- [ ] **G2** — `RuleRegistry`: `defaults()`, `of(...)`, ordered lists, `analysisRule(id)` lookup
      · verify: `RuleRegistryTest` — unique non-blank ids, duplicate rejected, lookup miss · dep: G1 · 10m
- [ ] **G3** — `AnalysisRuleContractTest` abstract + stub-rule subclass
      · verify: base assertions green against the stub · dep: G2 · 5m
- [ ] **G4** — `FindingPrinter` (single canonical finding format)
      · verify: `FindingPrinterTest` — golden line for a 2-id finding · dep: G1 · 5m

---

## Phase H — Categorization · PRD task 7 · 35m · cumulative 5h30

- [ ] **H1** — `KeywordCategorizationRule` + ordered keyword map (PRD §9.2 table)
      · verify: `KeywordCategorizationRuleTest` — first match wins, case-insensitive, no match → empty · dep: G1 · 10m
- [ ] **H2** — `CategorizationService.plan()` → immutable list of `(tx, from, to)` changes
      · verify: `CategorizationServiceTest` — only `UNCATEGORIZED`, manual values untouched · dep: H1, C7 · 10m
- [ ] **H3** — `categorize` command: dry-run + `--apply`; no write when plan empty
      · verify: write counter unchanged in dry-run and empty apply; file byte-identical · dep: H2 · 10m
- [ ] **H4** — `categorize --id N --category X` (alone and with `--apply`)
      · verify: unknown id → exit 2 no write; override survives later `--apply` · dep: H3 · 5m

---

## Phase I — Summary · PRD task 8 · 40m · cumulative 6h10

- [ ] **I1** — `SummaryService` totals: income, expenses, signed net, `--month` filter
      · verify: `SummaryServiceTest` — fixture totals, empty month, negative net · dep: G2, C7 · 10m
- [ ] **I2** — Category breakdown (expenses only), basis-point percentages, spend-desc ordering with name tiebreak
      · verify: `SummaryBreakdownTest` — ordering, tiebreak, zero-expense no-divide · dep: I1 · 10m
- [ ] **I3** — `SummaryFormatter`: aligned columns, `+`/`-` net, 1dp share, empty state
      · verify: `SummaryFormatterTest` — golden block matching PRD §5.6 · dep: I2 · 10m
- [ ] **I4** — `summary` command with `--month`
      · verify: `CliTest` — integration against temp CSV, all-time default · dep: I3, E2 · 10m

---

## Phase J — Analysis rule · PRD task 9 · 25m · cumulative 6h35

- [ ] **J1** — `DuplicateChargeRule` core: description normalization, cents equality, ±3-day window, expenses only
      · verify: `DuplicateChargeRuleTest` — day 3 vs 4, same-day, differing amount, income excluded · dep: G3 · 10m
- [ ] **J2** — Multiple pairs per transaction + deterministic ordering
      · verify: `DuplicateChargeRuleTest` — 3-transaction cluster, stable output across runs · dep: J1 · 5m
- [ ] **J3** — `analyze` command with `--rule` filter, wired to `FindingPrinter`
      · verify: `CliTest` — no findings → exit 0 `No findings.`; unknown `--rule` → exit 1 · dep: J2, G4, D7 · 10m

> **Milestone 3 — after J3:** feature complete. Every command in PRD §5 works. K/L make it reviewable.

---

## Phase K — Hardening · PRD task 10 · 45m · cumulative 7h20

- [ ] **K1** — `delete` command + service
      · verify: unknown id → exit 2 no write; deleted id never reused · dep: J3, E1 · 10m
- [ ] **K2** — `list --uncategorized`
      · verify: filter test + CLI test · dep: E3 · 5m
- [ ] **K3** — Corrupt-store behaviour (bad columns, bad amount, duplicate id, bad date) → `data/transactions.csv:7: ...`, exit 2, file untouched
      · verify: `CsvCorruptionTest` — 4 cases; fix codec gaps found · dep: C4 · 10m
- [ ] **K4** — CLI integration suite: drive `Cli` with args, capture stdout/stderr, assert exit codes
      · verify: ~8 end-to-end cases green · dep: K1, K2 · 10m
- [ ] **K5** — Golden lifecycle test replicating PRD §7.6 exactly (commands → final CSV text → summary output)
      · verify: byte-exact assertion against the PRD transcript · dep: K4 · 10m

---

## Phase L — Docs and release · PRD task 11 · 35m · cumulative 7h55

- [ ] **L1** — README: what it is, assumptions (PRD §4), build/run (Gradle 8.5+, no-wrapper rationale)
      · verify: reviewer can build from README alone · dep: A2 · 10m
- [ ] **L2** — README tour: run the PRD §7.6 transcript for real, paste actual output
      · verify: transcript matches K5 golden test · dep: K5 · 10m
- [ ] **L3** — README design notes + 4 extension recipes + SQLite porting map + limitations; final `gradle clean build` review
      · verify: PRD §18 checklist satisfied · dep: L2 · 15m

---

## Totals

| Phase | Tasks | Est | Cumulative |
|---|---|---|---|
| A | 2 | 20m | 0h20 |
| B | 4 | 40m | 1h00 |
| C | 8 | 70m | 2h10 |
| D | 7 | 65m | 3h15 |
| E | 3 | 40m | 3h55 |
| F | 3 | 35m | 4h30 |
| G | 4 | 25m | 4h55 |
| H | 4 | 35m | 5h30 |
| I | 4 | 40m | 6h10 |
| J | 3 | 25m | 6h35 |
| K | 5 | 45m | 7h20 |
| L | 3 | 35m | **7h55** |

50 tasks, ~7h55m. The ~20m above `PRD.md`'s 7h35m is per-commit overhead from splitting task 1 into 4 commits and task 2 into 8 — no added scope.

---

## Sequencing notes

- **Remote:** `origin` must stay `git@github.com-lawrencekcleung:lawrencekcleung/personal-finance-tracker.git`. Plain `github.com` authenticates as `nao921` and will reject pushes to this repo.
- **Critical path:** A1 → A2 → B1 → B2 → B4 → C1 → C2 → C3 → C4 → D1 → D3 → D4 → D5 → D7 → E1 → E2.
- **Parallel after D7:** F, G→H, and I are independent; J needs only G3.
- **If time is short:** stop at Milestone 2 (E3) and write L1. Do not drop C6 (contract test), C7 (in-memory fake), or J1 (the analysis rule).
- **Deviations from PRD §14 ordering**, all deliberate: exceptions at B3 (needed by C8/D7); in-memory repository at C7 instead of K (service tests need a fake); `FindingPrinter` at G4 before the analyze command.

---

## Definition of done (from PRD §18)

- [ ] `gradle build` and `gradle test` pass from a clean checkout with only JDK 21 and Gradle installed
- [ ] The PRD §7.6 transcript runs exactly as written, including exit codes
- [ ] CSV file matches the PRD §7.1 contract byte-for-byte after each command
- [ ] `categorize` idempotent, `analyze` non-persistent, `summary` matches a hand-checked total
- [ ] No binaries, no committed data, no IDE metadata
- [ ] README answers "why" for every non-obvious choice in PRD §2
