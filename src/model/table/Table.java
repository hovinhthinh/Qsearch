package model.table;

import java.util.HashMap;

public class Table {
    public transient static final int MAX_COLUMN_WIDTH = 50;
    public transient HashMap<String, Object> attributes = new HashMap<>(); // Used for any other purpose.

    public Cell[][] header; // row -> column
    public Cell[][] data; // row -> column
    public int nHeaderRow, nDataRow, nColumn;

    public boolean[] isNumericColumn; // TODO: this needs to be checked again using external tool, instead of using
    // internal signal from Wikipedia.
    public int[] quantityToEntityColumn;

    public String surroundingText;
    public String caption;

    public String source;

    public String getTableContentPrintable(boolean disambiguateEntity) {
        StringBuilder sb = new StringBuilder();
        int[] columnMaxWidth = new int[nColumn];

        boolean headerPrinted = true;
        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    columnMaxWidth[j] = Math.max(columnMaxWidth[j], Math.min(MAX_COLUMN_WIDTH,
                            (headerPrinted && isNumericColumn[j] ? 1 : 0) + (disambiguateEntity ? part[i][j].getDisambiguatedText().length() : part[i][j].text.length())));
                }
            }
            headerPrinted = false;
        }
        headerPrinted = true;
        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    String str = (headerPrinted && isNumericColumn[j] ? "*" : "") + (disambiguateEntity ? part[i][j].getDisambiguatedText() : part[i][j].text);
                    if (str.length() > columnMaxWidth[j]) {
                        str = str.substring(0, (columnMaxWidth[j] - 3) - (columnMaxWidth[j] - 3) / 2) + "..." +
                                str.substring(str.length() - (columnMaxWidth[j] - 3) / 2);
                    }
                    sb.append("[");
                    for (int k = 0; k < columnMaxWidth[j] - str.length(); ++k) {
                        sb.append(" ");
                    }
                    sb.append(str);
                    sb.append("] ");
                }
                sb.append("\r\n");
            }
            if (headerPrinted) {
                headerPrinted = false;
                for (int i = 0; i < columnMaxWidth.length; ++i) {
                    for (int j = 0; j < columnMaxWidth[i] + 2; ++j) {
                        sb.append("-");
                    }
                    if (i > 0) {
                        sb.append("-");
                    }
                }
                sb.append("\r\n");
            }

        }

        return sb.toString();
    }

    // Not checking the first column (this could be the index column).
    public boolean containsNumericColumn() {
        for (int i = 1; i < nColumn; ++i) {
            if (isNumericColumn[i]) {
                return true;
            }
        }
        return false;
    }
}
