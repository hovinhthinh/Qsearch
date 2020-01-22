package pipeline.text;

import model.text.Paragraph;

@Deprecated
public class TaggingPipeline {
    public String failNode;
    private TaggingNode[] taggingNodes;

    public TaggingPipeline(TaggingNode... nodes) {
        taggingNodes = nodes;
    }

    public boolean tag(Paragraph fact) {
        failNode = null;
        for (TaggingNode node : taggingNodes) {
            if (!node.process(fact)) {
                failNode = node.getClass().getName();
                return false;
            }
        }
        return true;
    }
}
