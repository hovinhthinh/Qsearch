package pipeline.text;

import model.text.Paragraph;
import model.text.Sentence;
import org.junit.Assert;

public abstract class FilteringNode implements TaggingNode {
    @Override
    public boolean process(Paragraph paragraph) {
        Assert.assertNotNull(paragraph.sentences);
        for (int i = paragraph.sentences.size() - 1; i >= 0; --i) {
            if (isFiltered(paragraph.sentences.get(i))) {
                paragraph.sentences.remove(i);
            }
        }
        return paragraph.sentences.size() > 0;
    }

    public abstract boolean isFiltered(Sentence sent);
}
