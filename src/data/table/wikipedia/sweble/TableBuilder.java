package data.table.wikipedia.sweble;

import java.util.HashMap;
import java.util.Map;

public class TableBuilder {

    private HashMap<String, Object> contentMap;

    private int nDuplicated;

    public TableBuilder() {
        contentMap = new HashMap<>();
        nDuplicated = 0;
    }

    public static void main(String[] args) {
        TableBuilder builder = new TableBuilder();
        builder.addHtmlCell(0, 0, 2, 2, "SPAN_1");
        builder.addHtmlCell(0, 1, "SPAN_2");
        builder.addHtmlCell(1, 0, 2, 1, "SPAN_3");
        builder.addHtmlCell(2, 0, 1, 2, "SPAN_4");
        System.out.println(builder.getSimplePrint());
    }

    public int getNDuplicatedNode() {
        return nDuplicated;
    }

    public int getNBlankNode() {
        int nBlank = 0;
        Object[][] table = getTable();
        for (Object[] r : table) {
            for (Object c : r) {
                if (c == null) {
                    ++nBlank;
                }
            }
        }
        return nBlank;
    }

    public void addHtmlCell(int htmlRowIndex, int htmlColIndex, int rowSpan, int colSpan, Object value) {
        while (contentMap.containsKey(String.format("%d\t%d", htmlRowIndex, htmlColIndex))) {
            ++htmlColIndex;
        }
        for (int i = 0; i < rowSpan; ++i) {
            for (int j = 0; j < colSpan; ++j) {
                Object currentValue = contentMap.put(String.format("%d\t%d", htmlRowIndex + i, htmlColIndex + j), value);
                if (currentValue != null) {
                    ++nDuplicated;
                }
            }
        }
    }

    public void addHtmlCell(int htmlRowIndex, int htmlColIndex, Object value) {
        addHtmlCell(htmlRowIndex, htmlColIndex, 1, 1, value);
    }

    public Object[][] getTable() {
        if (contentMap.isEmpty()) {
            return new Object[0][0];
        }
        int minRowIndex = -1, maxRowIndex = -1, minColIndex = -1, maxColIndex = -1;
        for (String k : contentMap.keySet()) {
            String[] rc = k.split("\t");
            int r = Integer.parseInt(rc[0]), c = Integer.parseInt(rc[1]);
            if (minRowIndex == -1 || minRowIndex > r) {
                minRowIndex = r;
            }
            if (maxRowIndex == -1 || maxRowIndex < r) {
                maxRowIndex = r;
            }
            if (minColIndex == -1 || minColIndex > c) {
                minColIndex = c;
            }
            if (maxColIndex == -1 || maxColIndex < c) {
                maxColIndex = c;
            }
        }
        Object[][] res = new Object[maxRowIndex - minRowIndex + 1][maxColIndex - minColIndex + 1];
        for (Map.Entry<String, Object> e : contentMap.entrySet()) {
            String[] rc = e.getKey().split("\t");
            int r = Integer.parseInt(rc[0]), c = Integer.parseInt(rc[1]);
            res[r - minRowIndex][c - minColIndex] = e.getValue();
        }
        return res;
    }

    public String getSimplePrint() {
        StringBuilder sb = new StringBuilder();
        Object[][] table = getTable();
        for (Object[] r : table) {
            for (Object c : r) {
                sb.append(c).append("\t");
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }
}
