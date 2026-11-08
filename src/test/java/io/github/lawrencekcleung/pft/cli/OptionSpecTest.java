package io.github.lawrencekcleung.pft.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionSpecTest {

    @Test
    void describesAnOptionAndItsDefault() {
        OptionSpec spec = new OptionSpec("category", "category name",
                OptionType.STRING, false, "UNCATEGORIZED");

        assertEquals("category", spec.name());
        assertEquals(OptionType.STRING, spec.type());
        assertTrue(spec.hasDefault());
    }

    @Test
    void requiredOptionCannotHaveADefault() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new OptionSpec("date", "the day", OptionType.DATE, true, "2026-11-01"));

        assertEquals("required option --date cannot have a default", e.getMessage());
    }

    @Test
    void flagCannotHaveADefault() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new OptionSpec("apply", "apply it", OptionType.FLAG, false, "true"));

        assertEquals("flag --apply takes no value, so it cannot have a default", e.getMessage());
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new OptionSpec(" ", "help", OptionType.STRING, false, null));
    }

    @Test
    void dashedNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new OptionSpec("-date", "help", OptionType.STRING, false, null));
    }

    @Test
    void optionWithoutDefaultReportsNoDefault() {
        OptionSpec spec = new OptionSpec("date", "the day", OptionType.DATE, true, null);

        assertFalse(spec.hasDefault());
    }
}
