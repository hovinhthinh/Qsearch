package pipeline;

import model.table.Table;

public class ColumnTypeTaggingNode implements TaggingNode {
    public static final double DEFAULT_ENTITY_THRESHOLD = 0.5;
    public static final double DEFAULT_QUANTITY_THRESHOLD = 0.5;

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
        table.isNumericColumn = new boolean[table.nColumn];
        table.isEntityColumn = new boolean[table.nColumn];
        for (int c = 0; c < table.nColumn; ++c) {
            int nEntity = 0, nQuantity = 0;
            for (int r = 0; r < table.nDataRow; ++r) {
                if (table.data[r][c].getRepresentativeEntityLink() != null) {
                    ++nEntity;
                } else if (table.data[r][c].getRepresentativeQuantityLink() != null) {
                    ++nQuantity;
                }
            }
            if (nEntity >= minEntityThreshold * table.nDataRow - 1e-6) {
                table.isEntityColumn[c] = true;
            } else if (nQuantity >= minQuantityThreshold * table.nDataRow - 1e-6) {
                table.isNumericColumn[c] = true;
            }
        }
        return true;
    }
}
