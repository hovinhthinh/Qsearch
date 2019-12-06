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
                new TablePrefilteringNode(),
                new QuantityTaggingNode(),
                new PriorBasedEntityTaggingNode(),
                new ColumnTypeTaggingNode(),
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
