package model.table;

import nlp.NLP;

import java.util.HashMap;

public class Table {
    public transient static final int MAX_COLUMN_WIDTH = 30;
    public transient HashMap<String, Object> attributes = new HashMap<>(); // Used for any other purpose.

    public String _id; // table id.

    public Cell[][] header; // row -> column
    private String[] combinedHeader;
    private String[] headerUnitSpan;
    public Cell[][] data; // row -> column
    public int nHeaderRow, nDataRow, nColumn;

    public String[] majorUnitInColumn; // null means dimensionless, or unavailable. (this is provided from QuantityTaggingNode)

    public boolean[] isNumericColumn;
    public boolean[] isEntityColumn;

    public int[] quantityToEntityColumn; // -1 means there is no connection.
    public double[] quantityToEntityColumnScore; // linking score (default is -1 | unknown).

    @SuppressWarnings("These cached values do not check includeUnit or not")
    public transient String[] cachedQuantityDescriptionFromCombinedHeader = null;
    public transient String[] cachedQuantityDescriptionFromLastHeader = null;

    @Deprecated
    public int keyColumn = -1; // computed based on the average linking scores from quantity columns

    @Deprecated
    public double keyColumnScore = -1.0;

    public String surroundingText;
    public String caption;
    public String pageTitle;

    public String source;

    public HashMap<String, String> moreInfo = new HashMap<>(); // More info, for different datasets.

    public String getTableContentPrintable(boolean showAnnotations, boolean multipleLine, boolean printColumnIndex) {
        return getTableContentPrintable(showAnnotations, multipleLine, printColumnIndex, false);
    }

    // for debugging TextBasedColumnScoringNode.
    public transient String[][] QfactMatchingStr;

    public String getTableContentPrintable(boolean showAnnotations, boolean multipleLine, boolean printColumnIndex, boolean singleLinkForCell) {
        StringBuilder sb = new StringBuilder();
        int[] columnMaxWidth = new int[nColumn];
        boolean printHeader = true;

        String[] linkingScoresStr = null;
        if (quantityToEntityColumn != null) {
            linkingScoresStr = new String[nColumn];
            for (int i = 0; i < nColumn; ++i) {
                linkingScoresStr[i] = !isNumericColumn[i] ? "" : (quantityToEntityColumnScore[i] == -1.0 ? "?" : String.format("%.3f", quantityToEntityColumnScore[i]));
                columnMaxWidth[i] = Math.max(columnMaxWidth[i], linkingScoresStr[i].length());
            }
        }

        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                for (int j = 0; j < part[i].length; ++j) {
                    columnMaxWidth[j] = Math.max(columnMaxWidth[j], (printHeader && showAnnotations && isNumericColumn[j] ? (quantityToEntityColumn == null || quantityToEntityColumn[j] == -1 ? 2 : String.valueOf(quantityToEntityColumn[j]).length() + 1) : 0)
                            + (showAnnotations ? part[i][j].getDisambiguatedText(singleLinkForCell).length() : part[i][j].text.length()));
                    columnMaxWidth[j] = Math.max(columnMaxWidth[j], (printHeader && showAnnotations && isEntityColumn[j] ? 1 : 0)
                            + (showAnnotations ? part[i][j].getDisambiguatedText(singleLinkForCell).length() : part[i][j].text.length()));
                    columnMaxWidth[j] = Math.min(columnMaxWidth[j], MAX_COLUMN_WIDTH);
                }
            }
            printHeader = false;
        }

        // print column index
        if (printColumnIndex) {
            for (int i = 0; i < nColumn; ++i) {
                columnMaxWidth[i] = Math.max(columnMaxWidth[i], String.valueOf(i).length() + 1);
                sb.append("> ");
                sb.append(i);
                for (int k = 0; k < columnMaxWidth[i] - String.valueOf(i).length() - 1; ++k) {
                    sb.append(" ");
                }
                sb.append("  ");
            }
            sb.append("\r\n");
        }

        // print linking scores
        if (showAnnotations && linkingScoresStr != null) {
            for (int i = 0; i < nColumn; ++i) {
                sb.append("⎡");
                sb.append(linkingScoresStr[i]);
                for (int k = 0; k < columnMaxWidth[i] - linkingScoresStr[i].length(); ++k) {
                    sb.append(" ");
                }
                sb.append("⎦ ");
            }
            sb.append("\r\n");
        }

        printHeader = true;
        for (Cell[][] part : new Cell[][][]{header, data}) {
            for (int i = 0; i < part.length; ++i) {
                String[] strs = new String[part[i].length];
                for (int j = 0; j < part[i].length; ++j) {
                    strs[j] = (printHeader && showAnnotations && isEntityColumn[j] ? "*" : "") +
                            (printHeader && showAnnotations && isNumericColumn[j] ? (quantityToEntityColumn == null || quantityToEntityColumn[j] == -1 ? "?@" : quantityToEntityColumn[j] + "@") : "")
                            + (showAnnotations ? part[i][j].getDisambiguatedText(singleLinkForCell) : part[i][j].text);
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

    private boolean isSpecialTokenToBeIgnored(String token) {
        if (token.equals("$")) {
            return false;
        }
        for (int i = 0; i < token.length(); ++i) {
            if (Character.isLetterOrDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public String getQuantityDescriptionFromCombinedHeader(int columnIndex, boolean includeUnit) {
        if (cachedQuantityDescriptionFromCombinedHeader == null) {
            cachedQuantityDescriptionFromCombinedHeader = new String[nColumn];
        }
        if (cachedQuantityDescriptionFromCombinedHeader[columnIndex] != null) {
            return cachedQuantityDescriptionFromCombinedHeader[columnIndex];
        }

        StringBuilder sb = new StringBuilder();
        for (String token : getCombinedHeader(columnIndex).toLowerCase().split(" ")) {
            if (NLP.BLOCKED_STOPWORDS.contains(token) || isSpecialTokenToBeIgnored(token)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(token);
        }
        if (includeUnit && majorUnitInColumn != null && majorUnitInColumn[columnIndex] != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            String unit = majorUnitInColumn[columnIndex].toLowerCase();
            if (unit.equals("us$")) {
                unit = "$";
            }
            sb.append(unit);
        }
        return cachedQuantityDescriptionFromCombinedHeader[columnIndex] = sb.toString();
    }

    public String getQuantityDescriptionFromCombinedHeader(int columnIndex) {
        return getQuantityDescriptionFromCombinedHeader(columnIndex, true);
    }

    public String getQuantityDescriptionFromLastHeader(int columnIndex, boolean includeUnit) {
        if (cachedQuantityDescriptionFromLastHeader == null) {
            cachedQuantityDescriptionFromLastHeader = new String[nColumn];
        }
        if (cachedQuantityDescriptionFromLastHeader[columnIndex] != null) {
            return cachedQuantityDescriptionFromLastHeader[columnIndex];
        }

        StringBuilder sb = new StringBuilder();
        for (String token : header[nHeaderRow - 1][columnIndex].text.toLowerCase().split(" ")) {
            if (NLP.BLOCKED_STOPWORDS.contains(token) || isSpecialTokenToBeIgnored(token)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(token);
        }
        if (includeUnit && majorUnitInColumn != null && majorUnitInColumn[columnIndex] != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            String unit = majorUnitInColumn[columnIndex].toLowerCase();
            if (unit.equals("us$")) {
                unit = "$";
            }
            sb.append(unit);
        }
        return cachedQuantityDescriptionFromLastHeader[columnIndex] = sb.toString();
    }

    public String getQuantityDescriptionFromLastHeader(int columnIndex) {
        return getQuantityDescriptionFromLastHeader(columnIndex, true);
    }

    // This call does cache combined header
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

    // This call does not cache combined header
    public String getOriginalCombinedHeader(int columnIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nHeaderRow; ++i) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(header[i][columnIndex].text);
        }
        return sb.toString();
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

    // Check if first column is index column
    public boolean hasIndexColumn() {
        boolean startWith0 = false, startWith1 = false;
        for (int i = 0; i < nDataRow; ++i) {
            try {
                String indexStr = data[i][0].text;
                if (indexStr.endsWith(".")) {
                    indexStr = indexStr.substring(0, indexStr.length() - 1).trim();
                }
                int rank = Integer.parseInt(indexStr);
                if (rank == i) {
                    startWith0 = true;
                } else if (rank == i + 1) {
                    startWith1 = true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return startWith0 ^ startWith1;
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

    public int getFirstNonNumericColumn() {
        for (int i = 0; i < nColumn; ++i) {
            if (!isNumericColumn[i]) {
                return i;
            }
        }
        return -1;
    }
}
