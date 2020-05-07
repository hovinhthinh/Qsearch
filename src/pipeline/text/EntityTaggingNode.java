package pipeline.text;

import model.text.Paragraph;

@Deprecated
public class EntityTaggingNode implements TaggingNode {
    @Override
    public boolean process(Paragraph paragraph) {
        return false;
    }
}
