package pipeline;

import model.table.Table;

public class TaggingPipeline {
    public String failNode;
    private TaggingNode[] taggingNodes;

    public TaggingPipeline(TaggingNode... nodes) {
        taggingNodes = nodes;
    }

    @Deprecated
    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new EntityTaggingNode(),
                new EntityFilteringNode(),
                new QuantityTaggingNode(),
                new QuantityFilteringNode(),
                new DeepColumnScoringNode(),
                new PostFilteringNode()
        );
    }

    public boolean tag(Table table) {
        failNode = null;
        for (TaggingNode node : taggingNodes) {
            if (!node.process(table)) {
                failNode = node.getClass().getName();
                return false;
            }
        }
        return true;
    }
}
