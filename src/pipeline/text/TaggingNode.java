package pipeline.text;

import model.text.Paragraph;

@Deprecated
public interface TaggingNode {
    boolean process(Paragraph paragraph);
}
