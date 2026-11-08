package io.github.lawrencekcleung.pft.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Money#parse(String)}: decimal text to exact cents, canonical round trips and
 * rejected input.
 */
class MoneyParseTest {

    @Test
    void parseConvertsDecimalTextToCents() {
        assertEquals(Money.ofCents(4200), Money.parse("42"));
        assertEquals(Money.ofCents(4250), Money.parse("42.5"));
        assertEquals(Money.ofCents(4250), Money.parse("42.50"));
        assertEquals(Money.ofCents(0), Money.parse("0.00"));
        assertEquals(Money.ofCents(500_000_000), Money.parse("5000000.00"));
        assertEquals(Money.ofCents(750), Money.parse("007.5"));
        assertEquals(Money.ofCents(4250), Money.parse("  42.5  "));
    }

    @Test
    void parseRoundTripsThroughFormat() {
        String[] inputs = {"42", "42.5", "42.50", "0.00", "5000000.00", "007.5"};
        String[] canonical = {"42.00", "42.50", "42.50", "0.00", "5000000.00", "7.50"};
        for (int i = 0; i < inputs.length; i++) {
            assertEquals(canonical[i], Money.parse(inputs[i]).format(), inputs[i]);
        }
    }

    @Test
    void parseRejectsBlankAndNull() {
        IllegalArgumentException nullError =
                assertThrows(IllegalArgumentException.class, () -> Money.parse(null));
        assertTrue(nullError.getMessage().contains("amount is required"));

        IllegalArgumentException emptyError =
                assertThrows(IllegalArgumentException.class, () -> Money.parse(""));
        assertTrue(emptyError.getMessage().contains("amount is required"));

        IllegalArgumentException spacesError =
                assertThrows(IllegalArgumentException.class, () -> Money.parse("   "));
        assertTrue(spacesError.getMessage().contains("amount is required"));
    }

    @Test
    void parseRejectsInvalidSyntax() {
        String[] invalid = {"42.505", "-1.00", "1,234.50", ".50", "abc", "1e3"};
        for (String raw : invalid) {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> Money.parse(raw), raw);
            assertTrue(error.getMessage().contains("invalid amount"), raw);
            assertTrue(error.getMessage().contains(raw), raw);
        }
    }

    @Test
    void parseRejectsAmountsThatOverflowCents() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> Money.parse("999999999999999999"));
        assertTrue(error.getMessage().contains("amount out of range"));
    }

    @Test
    void parseTreatsEqualAmountsAsEqual() {
        assertEquals(Money.parse("1.0"), Money.parse("1.00"));
        assertEquals(Money.parse("1.0").hashCode(), Money.parse("1.00").hashCode());
    }
}
