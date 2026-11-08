package io.github.lawrencekcleung.pft.cli;

import java.util.List;

/**
 * Renders an aligned text table.
 *
 * <p>Columns are separated by two spaces and padded to the widest header or cell. Columns listed
 * in {@code rightAlignedColumns} (typically amounts and ids) are padded on the left; everything
 * else is padded on the right. The last column is never padded, so lines carry no trailing
 * spaces.</p>
 */
public final class TablePrinter {

    private static final String SEPARATOR = "  ";

    /**
     * Renders the table.
     *
     * @param headers             the column headers
     * @param rows                the cell values, one list per row
     * @param rightAlignedColumns zero-based indexes of columns to right-align
     * @return the table as text, without a trailing newline
     * @throws IllegalArgumentException if headers are empty, a row has the wrong width, or an
     *                                  alignment index is out of range
     */
    public String render(List<String> headers, List<List<String>> rows,
                         int... rightAlignedColumns) {
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("at least one column is required");
        }
        for (int column : rightAlignedColumns) {
            if (column < 0 || column >= headers.size()) {
                throw new IllegalArgumentException("column index out of range: " + column);
            }
        }

        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }
        for (List<String> row : rows) {
            if (row.size() != headers.size()) {
                throw new IllegalArgumentException(
                        "expected " + headers.size() + " cells, got " + row.size());
            }
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }

        StringBuilder text = new StringBuilder();
        appendRow(text, headers, widths, rightAlignedColumns);
        for (List<String> row : rows) {
            text.append('\n');
            appendRow(text, row, widths, rightAlignedColumns);
        }
        return text.toString();
    }

    private static void appendRow(StringBuilder text, List<String> cells, int[] widths,
                                  int[] rightAlignedColumns) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                text.append(SEPARATOR);
            }
            String cell = cells.get(i);
            if (isRightAligned(i, rightAlignedColumns)) {
                text.append(" ".repeat(widths[i] - cell.length())).append(cell);
            } else {
                text.append(cell);
                if (i < cells.size() - 1) {
                    text.append(" ".repeat(widths[i] - cell.length()));
                }
            }
        }
    }

    private static boolean isRightAligned(int column, int[] rightAlignedColumns) {
        for (int rightAligned : rightAlignedColumns) {
            if (rightAligned == column) {
                return true;
            }
        }
        return false;
    }
}
