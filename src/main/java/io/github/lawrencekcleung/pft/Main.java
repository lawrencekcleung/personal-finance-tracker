package io.github.lawrencekcleung.pft;

/**
 * Entry point for the personal finance tracker.
 *
 * <p>At this stage the application only prints its usage stub. The command framework and
 * transaction handling arrive in later tasks; see {@code tasks.md} for the order.</p>
 */
public final class Main {

    public static final String NAME = "pft";

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println(usage());
    }

    /**
     * Usage text for the scaffold. Replaced by the help generated from command
     * specifications in task D6.
     */
    static String usage() {
        return """
                %s - personal finance tracker

                Usage:
                  %s <command> [options]

                Commands (implemented in later tasks):
                  add         record a single income or expense
                  import      bulk import transactions from a CSV file
                  list        list transactions, optionally filtered
                  categorize  assign categories, manually or by keyword rules
                  summary     totals and spend by category
                  analyze     run rule-based analysis
                  delete      remove a transaction

                Options:
                  --data <path>   data file (default: ./data/transactions.csv)
                  --help          show this help
                """.formatted(NAME, NAME);
    }
}
