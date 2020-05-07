package pipeline.text;

import model.text.Sentence;
import org.junit.Assert;

public class PostFilteringNode extends FilteringNode {
    @Override
    public boolean isFiltered(Sentence sent) {
        Assert.assertNotNull(sent.quantitativeFacts);
        Assert.assertNotNull(sent.negativeQuantitativeFacts);
        return sent.quantitativeFacts.size() == 0 && sent.negativeQuantitativeFacts.size() == 0;
    }
}
