package io.github.lawrencekcleung.pft.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TablePrinterTest {

    private final TablePrinter printer = new TablePrinter();

    @Test
    void padsColumnsToTheWidestCell() {
        String table = printer.render(
                List.of("Name", "City"),
                List.of(List.of("Ada", "London"), List.of("Grace", "NY")));

        assertEquals("""
                Name   City
                Ada    London
                Grace  NY""", table);
    }

    @Test
    void rightAlignsRequestedColumns() {
        String table = printer.render(
                List.of("ID", "Amount"),
                List.of(List.of("1", "5.00"), List.of("20", "100.00")),
                0, 1);

        assertEquals("""
                ID  Amount
                 1    5.00
                20  100.00""", table);
    }

    @Test
    void neverLeavesTrailingSpaces() {
        String table = printer.render(
                List.of("ID", "Note"),
                List.of(List.of("1", "short"), List.of("22", "x")));

        for (String line : table.split("\n")) {
            assertFalse(line.endsWith(" "), () -> "trailing space in: '" + line + "'");
        }
    }

    @Test
    void rendersHeadersOnly() {
        assertEquals("ID  Note", printer.render(List.of("ID", "Note"), List.of()));
    }

    @Test
    void rejectsRowsWithTheWrongWidth() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> printer.render(List.of("A", "B"), List.of(List.of("one"))));

        assertEquals("expected 2 cells, got 1", e.getMessage());
    }

    @Test
    void rejectsAnOutOfRangeAlignmentIndex() {
        assertThrows(IllegalArgumentException.class,
                () -> printer.render(List.of("A"), List.of(), 1));
    }
}
