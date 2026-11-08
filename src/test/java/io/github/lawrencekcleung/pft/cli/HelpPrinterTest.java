package io.github.lawrencekcleung.pft.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpPrinterTest {

    private static final CommandDefinition ADD = new CommandDefinition(
            "add", "record a single transaction",
            List.of(
                    new OptionSpec("date", "the day", OptionType.DATE, true, null),
                    new OptionSpec("amount", "how much", OptionType.AMOUNT, true, null),
                    new OptionSpec("category", "category name", OptionType.STRING,
                            false, "UNCATEGORIZED")),
            (args, ctx) -> { });

    private final HelpPrinter help = new HelpPrinter();

    @Test
    void rootHelpWithoutCommands() {
        String expected = """
                pft - personal finance tracker

                Usage:
                  pft [--data <path>] <command> [options]
                  pft --help
                  pft <command> --help

                Commands:
                  (none registered yet)

                Options:
                  --data <path>  data file (default: ./data/transactions.csv, or $PFT_DATA)
                  --help         show help for this command""";

        assertEquals(expected,
                help.root(new CommandRegistry(List.of())));
    }

    @Test
    void rootHelpListsEveryCommandWithItsSummary() {
        String expected = """
                pft - personal finance tracker

                Usage:
                  pft [--data <path>] <command> [options]
                  pft --help
                  pft <command> --help

                Commands:
                  add  record a single transaction

                Options:
                  --data <path>  data file (default: ./data/transactions.csv, or $PFT_DATA)
                  --help         show help for this command""";

        assertEquals(expected,
                help.root(new CommandRegistry(List.of(ADD))));
    }

    @Test
    void commandHelpListsEveryOptionIncludingGlobals() {
        String expected = """
                Usage: pft add [options]

                record a single transaction

                Options:
                  --date <yyyy-MM-dd>  the day (required)
                  --amount <amount>    how much (required)
                  --category <value>   category name (default: UNCATEGORIZED)
                  --data <path>        data file (default: ./data/transactions.csv, or $PFT_DATA)
                  --help               show help for this command""";

        assertEquals(expected, help.command(ADD));
    }

    @Test
    void everyDeclaredOptionAppearsInCommandHelp() {
        String text = help.command(ADD);

        for (OptionSpec option : ADD.allOptions()) {
            assertTrue(text.contains("--" + option.name()),
                    () -> "help is missing option --" + option.name());
        }
    }

    @Test
    void rootHelpMentionsEveryGlobalsAndUsageForm() {
        String text = help.root(new CommandRegistry(List.of(ADD)));

        assertTrue(text.contains("--data <path>"));
        assertTrue(text.contains("--help"));
        assertTrue(text.contains("pft <command> --help"));
    }
}
