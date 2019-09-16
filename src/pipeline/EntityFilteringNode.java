package pipeline;

import model.table.Table;
import model.text.Sentence;
import org.junit.Assert;

@Deprecated
public class EntityFilteringNode implements TaggingNode {
    @Override
    public boolean process(Table table) {
        return false;
    }
}
