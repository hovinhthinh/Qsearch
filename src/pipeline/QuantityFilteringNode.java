package pipeline;

import model.table.Table;

@Deprecated
public class QuantityFilteringNode implements TaggingNode {
    @Override
    public boolean process(Table table) {
        return false;
    }
}
