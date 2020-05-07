package pipeline.text;

import model.text.Sentence;
import org.junit.Assert;

public class EntityFilteringNode extends FilteringNode {
    @Override
    public boolean isFiltered(Sentence sent) {
        Assert.assertNotNull(sent.entityTags);
        return sent.entityTags.size() == 0;
    }
}
