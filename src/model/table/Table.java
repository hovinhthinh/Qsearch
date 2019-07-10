package model.table;

public class Table {
    public static final int MAX_COLUMN_WIDTH = 35;
    public Cell[][] header; // row -> column
    public Cell[][] data; // row -> column
    public int nHeaderRow, nDataRow, nColumn;

    public String surroundingText;

    public String source;

    public String getTableContentPrintable() {
        StringBuilder sb = new StringBuilder();
        int[] columnMaxWidth = new int[nColumn];

        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    columnMaxWidth[j] = Math.max(columnMaxWidth[j], Math.min(MAX_COLUMN_WIDTH, part[i][j].text.length()));
                }
            }
        }

        boolean headerPrinted = true;
        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    String str = part[i][j].text;
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
}
