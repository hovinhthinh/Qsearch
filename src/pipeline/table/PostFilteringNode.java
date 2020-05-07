package pipeline.table;

import model.table.Table;

@Deprecated
public class PostFilteringNode implements TaggingNode {
    @Override
    public boolean process(Table table) {
        return true;
    }
}
