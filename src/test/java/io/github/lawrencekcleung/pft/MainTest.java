package io.github.lawrencekcleung.pft;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the scaffold and, by existing, that the JUnit 5 platform is wired correctly.
 * The real test suites start at task B1.
 */
class MainTest {

    @Test
    void usageMentionsProgramName() {
        assertTrue(Main.usage().contains("pft"));
    }

    @Test
    void usageListsEveryCommandFromThePrd() {
        String usage = Main.usage();
        for (String command : new String[] {"add", "import", "list", "categorize",
                                            "summary", "analyze", "delete"}) {
            assertTrue(usage.contains(command), () -> "usage is missing command: " + command);
        }
    }
}
