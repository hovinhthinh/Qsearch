package pipeline;

import model.table.Table;
import pipeline.deep.DeepScoringClient;

import java.util.concurrent.ArrayBlockingQueue;

public class TaggingPipeline {
    public String failNode;
    private TaggingNode[] taggingNodes;

    public TaggingPipeline(TaggingNode... nodes) {
        taggingNodes = nodes;
    }

    @Deprecated
    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                new TimeTaggingNode(),
                new QuantityTaggingNode(),
                new PriorBasedEntityTaggingNode(),
                new ColumnTypeTaggingNode(),
                new DeepColumnScoringNode(DeepColumnScoringNode.JOINT_INFERENCE),
                new ColumnLinkFilteringNode(0),
                new PostFilteringNode()
        );
    }

    // Just the annotations of entities and quantities, there is no linking.
    public static TaggingPipeline getAnnotationPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                new TimeTaggingNode(),
                new QuantityTaggingNode(),
                new PriorBasedEntityTaggingNode(),
                new ColumnTypeTaggingNode()
        );
    }

    // Just the linking pipeline.
    public static TaggingPipeline getColumnLinkingPipeline(ArrayBlockingQueue<DeepScoringClient> clients) {
        return new TaggingPipeline(
                new DeepColumnScoringNode(DeepColumnScoringNode.JOINT_INFERENCE, clients),
                new ColumnLinkFilteringNode(0),
                new PostFilteringNode()
        );
    }

    // Just the linking pipeline.
    public static TaggingPipeline getColumnLinkingPipeline() {
        return new TaggingPipeline(
                new DeepColumnScoringNode(DeepColumnScoringNode.JOINT_INFERENCE),
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
