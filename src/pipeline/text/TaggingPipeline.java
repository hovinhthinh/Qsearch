package pipeline.text;

import model.text.Paragraph;

public class TaggingPipeline {
    public String failNode;
    private TaggingNode[] taggingNodes;

    public TaggingPipeline(TaggingNode... nodes) {
        taggingNodes = nodes;
    }

    @Deprecated
    public static TaggingPipeline getDefaultTaggingPipeline(String deepModelPath) {
        return new TaggingPipeline(
                new EntityTaggingNode(),
                new TimeTaggingNode(),
                new SentenceLengthFiltering(4, 40),
                new EntityFilteringNode(),
                new QuantityTaggingNode(),
                new QuantityFilteringNode(),
                new DeepTaggingNode(deepModelPath),
                new PostFilteringNode()
        );
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
