package pipeline.text;

import model.text.Paragraph;

public interface TaggingNode {
    boolean process(Paragraph paragraph);
}
