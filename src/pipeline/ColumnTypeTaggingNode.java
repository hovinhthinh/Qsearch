package pipeline;

import model.table.Table;

public class ColumnTypeTaggingNode implements TaggingNode {
    public static final double DEFAULT_ENTITY_THRESHOLD = 0.8;
    public static final double DEFAULT_QUANTITY_THRESHOLD = 0.8;

    public enum ColumnType {
        ENTITY, QUANTITY, OTHER
    }

    private double minEntityThreshold, minQuantityThreshold;

    public ColumnTypeTaggingNode(double minEntityThreshold, double minQuantityThreshold) {
        this.minEntityThreshold = minEntityThreshold;
        this.minQuantityThreshold = minQuantityThreshold;
    }

    public ColumnTypeTaggingNode() {
        this(DEFAULT_ENTITY_THRESHOLD, DEFAULT_QUANTITY_THRESHOLD);
    }

    @Override
    public boolean process(Table table) {
        table.columnType = new ColumnType[table.nColumn];
        table.isNumericColumn = new boolean[table.nColumn];
        for (int c = 0; c < table.nColumn; ++c) {
            int nEntity = 0, nQuantity = 0;
            for (int r = 0; r < table.nDataRow; ++r) {
                if (table.data[r][c].entityLinks.size() > 0) {
                    ++nEntity;
                } else if (table.data[r][c].quantityLinks.size() > 0) {
                    ++nQuantity;
                }
            }
            if (nEntity >= minEntityThreshold * table.nDataRow - 1e-6) {
                table.columnType[c] = ColumnType.ENTITY;
            } else if (nQuantity >= minQuantityThreshold * table.nDataRow - 1e-6) {
                table.columnType[c] = ColumnType.QUANTITY;
                table.isNumericColumn[c] = true;
            } else {
                table.columnType[c] = ColumnType.OTHER;
            }
        }
        return true;
    }
}
