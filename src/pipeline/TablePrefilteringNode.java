package pipeline;


import model.table.Cell;
import model.table.Table;

// Remove empty lines, remove tables with less than <minNDataRow> or more than <maxNDataRow> data rows.
public class TablePrefilteringNode implements TaggingNode {
    private int minNDataRow;
    private int maxNDataRow;

    public TablePrefilteringNode(int minNDataRow, int maxNDataRow) {
        this.minNDataRow = minNDataRow;
        this.maxNDataRow = maxNDataRow;
    }

    public TablePrefilteringNode() {
        this(4, 400);
    }

    private Cell[][] pruneEmptyRows(Cell[][] data) {
        boolean[] rowTobePruned = new boolean[data.length];
        int newNRow = 0;

        for (int i = 0; i < data.length; ++i) {
            rowTobePruned[i] = true;
            for (int j = 0; j < data[i].length; ++j) {
                if (!data[i][j].text.isEmpty()) {
                    rowTobePruned[i] = false;
                    ++newNRow;
                    break;
                }
            }
        }

        Cell[][] newData = new Cell[newNRow][];
        int currentRow = 0;
        for (int i = 0; i < data.length; ++i) {
            if (rowTobePruned[i]) {
                continue;
            }
            newData[currentRow++] = data[i];
        }
        return newData;
    }

    @Override
    public boolean process(Table table) {
        // Remove empty data rows
        table.data = pruneEmptyRows(table.data);
        table.nDataRow = table.data.length;

        table.header = pruneEmptyRows(table.header);
        table.nHeaderRow = table.header.length;

        if (table.nHeaderRow == 0 || table.nDataRow < minNDataRow || table.nDataRow > maxNDataRow) {
            return false;
        }

        return true;
    }
}
