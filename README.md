# personal-finance-tracker

A small command-line tracker for personal income and expenses: import transactions,
categorize them on demand, get spend summaries, and flag suspicious activity with
pluggable rules.

Status: in progress. `PRD.md` holds the design and `tasks.md` the implementation
checklist.

## Build and run

JDK 21 and Gradle 8.5+ are required. No Gradle wrapper is committed (it bundles a
binary jar); run `gradle wrapper` locally if you prefer one.

```console
gradle test
gradle installDist
build/install/pft/bin/pft --help
```

The build scaffolding lands in task A2, so the commands above become available then.

## Persistence

Transactions live in `data/transactions.csv` (gitignored, created on first write).
Amounts are integer cents formatted at two decimal places; no currency symbol.
