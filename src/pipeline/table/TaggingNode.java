package pipeline.table;

import model.table.Table;

public interface TaggingNode {
    boolean process(Table table);
}
