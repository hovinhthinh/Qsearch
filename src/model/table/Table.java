package model.table;

import java.util.HashMap;

public class Table {
    public transient static final int MAX_COLUMN_WIDTH = 30;
    public transient HashMap<String, Object> attributes = new HashMap<>(); // Used for any other purpose.

    public Cell[][] header; // row -> column
    private String[] combinedHeader;
    private String[] headerUnitSpan;
    public Cell[][] data; // row -> column
    public int nHeaderRow, nDataRow, nColumn;

    public boolean[] isNumericColumn; // TODO: this needs to be checked again using external tool, instead of using internal signal from Wikipedia.

    public int[] quantityToEntityColumn; // -1 means there is no connection.

    public String surroundingText;
    public String caption;

    public String source;

    public String getTableContentPrintable(boolean showAnnotations, boolean multipleLine) {
        StringBuilder sb = new StringBuilder();
        int[] columnMaxWidth = new int[nColumn];
        boolean printHeader = true;
        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    columnMaxWidth[j] = Math.max(columnMaxWidth[j], Math.min(MAX_COLUMN_WIDTH,
                            (printHeader && isNumericColumn[j] ? (quantityToEntityColumn == null || quantityToEntityColumn[j] == -1 ? 2 : String.valueOf(quantityToEntityColumn[j]).length() + 1) : 0)
                                    + (showAnnotations ? part[i][j].getDisambiguatedText().length() : part[i][j].text.length())));
                }
            }
            printHeader = false;
        }
        printHeader = true;
        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                String[] strs = new String[part[i].length];
                for (int j = 0; j < part[i].length; ++j) {
                    strs[j] = (printHeader && isNumericColumn[j] ? (quantityToEntityColumn == null || quantityToEntityColumn[j] == -1 ? "?@" : quantityToEntityColumn[j] + "@") : "")
                            + (showAnnotations ? part[i][j].getDisambiguatedText() : part[i][j].text);
                }
                if (!multipleLine) {
                    for (int j = 0; j < part[i].length; ++j) {
                        if (strs[j].length() > columnMaxWidth[j]) {
                            strs[j] = strs[j].substring(0, (columnMaxWidth[j] - 3) - (columnMaxWidth[j] - 3) / 2) + "..." +
                                    strs[j].substring(strs[j].length() - (columnMaxWidth[j] - 3) / 2);
                        }
                        sb.append("⎡");
                        sb.append(strs[j]);
                        for (int k = 0; k < columnMaxWidth[j] - strs[j].length(); ++k) {
                            sb.append(" ");
                        }
                        sb.append("⎦ ");
                    }
                    sb.append("\r\n");
                } else {
                    int nLine = 0;
                    for (int j = 0; j < part[i].length; ++j) {
                        int cellLength = strs[j].length();
                        nLine = Math.max(nLine, cellLength == 0 ? 1 : (cellLength - 1) / columnMaxWidth[j] + 1);
                    }
                    for (int l = 0; l < nLine; ++l) {
                        for (int j = 0; j < part[i].length; ++j) {
                            sb.append(l == 0 ? "⎡" : "⎜");
                            int startIndex = l * columnMaxWidth[j], endIndex = (l + 1) * columnMaxWidth[j];
                            String substr = endIndex < strs[j].length()
                                    ? strs[j].substring(startIndex, endIndex)
                                    : (startIndex < strs[j].length() ? strs[j].substring(startIndex) : "");
                            sb.append(substr);
                            for (int k = 0; k < columnMaxWidth[j] - substr.length(); ++k) {
                                sb.append(" ");
                            }
                            sb.append(l == nLine - 1 ? "⎦ " : "⎟ ");
                        }
                        sb.append("\r\n");
                    }
                }
            }
            if (printHeader) {
                printHeader = false;
                for (int i = 0; i < columnMaxWidth.length; ++i) {
                    for (int j = 0; j < columnMaxWidth[i] + 2; ++j) {
                        sb.append("─");
                    }
                    if (i > 0) {
                        sb.append("─");
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

    public String getCombinedHeader(int columnIndex) {
        if (combinedHeader == null) {
            combinedHeader = new String[nColumn];
        }
        if (combinedHeader[columnIndex] != null) {
            return combinedHeader[columnIndex];
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nHeaderRow; ++i) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(header[i][columnIndex].text);
        }

        return combinedHeader[columnIndex] = sb.toString();
    }

    public void setCombinedHeader(int columnIndex, String newCombinedHeader) {
        if (combinedHeader == null) {
            combinedHeader = new String[nColumn];
        }
        combinedHeader[columnIndex] = newCombinedHeader;
    }

    public void setHeaderUnitSpan(int columnIndex, String unitSpan) {
        if (headerUnitSpan == null) {
            headerUnitSpan = new String[nColumn];
        }
        headerUnitSpan[columnIndex] = unitSpan;
    }

    public String getHeaderUnitSpan(int columnIndex) {
        if (headerUnitSpan == null) {
            return null;
        }
        return headerUnitSpan[columnIndex];
    }

    public boolean hasCellWith2EntitiesOrQuantities() {
        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    if (part[i][j].entityLinks.size() + part[i][j].quantityLinks.size() > 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean hasCellWith2Entities() {
        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    if (part[i][j].entityLinks.size() > 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
