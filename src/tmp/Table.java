package tmp;

public class Table {
    public static final int MAX_COLUMN_WIDTH = 35;
    String[][] header; // row -> column
    String[][] data; // row -> column

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int nColumns = 0;
        for (int i = 0; i < header.length; ++i) {
            nColumns = Math.max(nColumns, header[i].length);
        }
        for (int i = 0; i < data.length; ++i) {
            nColumns = Math.max(nColumns, data[i].length);
        }
        int[] columnMaxWidth = new int[nColumns];
        for (String[][] part : new String[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    columnMaxWidth[j] = Math.max(columnMaxWidth[j], Math.min(MAX_COLUMN_WIDTH, part[i][j].length()));
                }
            }
        }
        boolean headerPrinted = true;
        for (String[][] part : new String[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    String str = part[i][j];
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
