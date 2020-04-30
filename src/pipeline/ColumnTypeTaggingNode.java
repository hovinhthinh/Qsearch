package pipeline;

import model.table.Table;

public class ColumnTypeTaggingNode implements TaggingNode {
    public static final double DEFAULT_ENTITY_THRESHOLD = 0.3;
    public static final double DEFAULT_QUANTITY_THRESHOLD = 0.3;

    private double minEntityThreshold, minQuantityThreshold;

    // set threshold to 0 to disable tagging.
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
        boolean hasEntityColumn = false, hasQuantityColumn = false;
        for (int c = 0; c < table.nColumn; ++c) {

            if (c == 0 && table.hasIndexColumn()) {
                // ignore index column
                continue;
            }
            
            int nEntity = 0, nQuantity = 0;
            for (int r = 0; r < table.nDataRow; ++r) {
                if (table.data[r][c].getRepresentativeEntityLink() != null) {
                    ++nEntity;
                } else if (table.data[r][c].getRepresentativeQuantityLink() != null) {
                    ++nQuantity;
                }
            }
            if (minEntityThreshold > 0 && nEntity >= minEntityThreshold * table.nDataRow - 1e-6) {
                table.isEntityColumn[c] = true;
                hasEntityColumn = true;
            } else if (minQuantityThreshold > 0 && nQuantity >= minQuantityThreshold * table.nDataRow - 1e-6) {
                table.isNumericColumn[c] = true;
                hasQuantityColumn = true;
            }
        }
        return (hasEntityColumn || minEntityThreshold == 0) && (hasQuantityColumn || minQuantityThreshold == 0);
    }
}
