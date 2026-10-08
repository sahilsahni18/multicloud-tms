package com.trackflow.tms.util;

/**
 * Minimal RFC 4180 CSV builder. Cells that start with = + - @ are prefixed
 * with ' so spreadsheet apps do not execute them as formulas (CSV injection).
 */
public final class CsvWriter {

    private final StringBuilder out = new StringBuilder();

    public CsvWriter(String... header) {
        row((Object[]) header);
    }

    public CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    static String escape(Object cell) {
        if (cell == null) {
            return "";
        }
        String value = cell.toString();
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            value = '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
