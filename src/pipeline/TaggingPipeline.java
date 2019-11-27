package pipeline;

import model.table.Table;

public class TaggingPipeline {
    public String failNode;
    private TaggingNode[] taggingNodes;

    public TaggingPipeline(TaggingNode... nodes) {
        taggingNodes = nodes;
    }

    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new PriorBasedEntityTaggingNode(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode(0.3, 0.3),
                new DeepColumnScoringNode(),
                new ColumnLinkFilteringNode(0),
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
