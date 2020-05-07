package pipeline.text;

import model.text.Sentence;
import org.junit.Assert;

public class QuantityFilteringNode extends FilteringNode {
    @Override
    public boolean isFiltered(Sentence sent) {
        Assert.assertNotNull(sent.quantityTags);
        return sent.quantityTags.size() == 0;
    }
}
