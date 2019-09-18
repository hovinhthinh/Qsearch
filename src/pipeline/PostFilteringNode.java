package pipeline;

import model.table.Table;
import model.text.Sentence;
import org.junit.Assert;

@Deprecated
public class PostFilteringNode implements TaggingNode {
    @Override
    public boolean process(Table table) {
        return true;
    }
}
