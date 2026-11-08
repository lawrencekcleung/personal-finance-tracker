package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.rules.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FindingPrinterTest {

    private final FindingPrinter printer = new FindingPrinter();

    @Test
    void rendersTheCanonicalTwoIdLine() {
        Finding finding = new Finding("duplicate-charge", List.of(8, 9),
                "9.90 \"Coffee\" on 2026-11-06 and 2026-11-07 (1 day apart)");

        assertEquals("[duplicate-charge] #8, #9  9.90 \"Coffee\" on 2026-11-06 and "
                + "2026-11-07 (1 day apart)", printer.format(finding));
    }

    @Test
    void rendersASingleIdLine() {
        Finding finding = new Finding("rule", List.of(4), "something happened");

        assertEquals("[rule] #4  something happened", printer.format(finding));
    }

    @Test
    void rendersAFindingWithoutIds() {
        Finding finding = new Finding("rule", List.of(), "nothing to point at");

        assertEquals("[rule] nothing to point at", printer.format(finding));
    }

    @Test
    void emitsNoTrailingWhitespace() {
        String line = printer.format(new Finding("rule", List.of(1, 2), "message"));

        assertFalse(line.endsWith(" "));
    }

    @Test
    void findingRequiresAnIdAndAMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> new Finding(" ", List.of(1), "message"));
        assertThrows(IllegalArgumentException.class,
                () -> new Finding("rule", List.of(1), " "));
    }
}
