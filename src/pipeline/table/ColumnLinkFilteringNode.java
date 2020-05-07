package pipeline.table;

import model.table.Table;

// Remove quantity-entity links based on min conf.
public class ColumnLinkFilteringNode implements TaggingNode {
    private double minConf;

    public ColumnLinkFilteringNode(double minLinkingConf) {
        this.minConf = minLinkingConf;
    }

    @Override
    public boolean process(Table table) {
        int nRemaningLinks = 0;
        for (int i = 0; i < table.nColumn; ++i) {
            if (table.isNumericColumn[i] && table.quantityToEntityColumn[i] != -1) {
                ++nRemaningLinks;
                if (table.quantityToEntityColumnScore[i] < minConf) {
                    table.quantityToEntityColumnScore[i] = -1.0;
                    table.quantityToEntityColumn[i] = -1;
                    --nRemaningLinks;
                }
            }
        }
        return nRemaningLinks > 0;
    }
}

