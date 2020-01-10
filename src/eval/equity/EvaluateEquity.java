package eval.equity;

import com.google.gson.Gson;
import eval.TruthTable;
import pipeline.*;
import util.FileUtils;

public class EvaluateEquity {
    public static TaggingPipeline getPipeline() {
        return new TaggingPipeline(
                new DeepColumnScoringNode(DeepColumnScoringNode.JOINT_INFERENCE),
                new ColumnLinkFilteringNode(0),
                new PostFilteringNode()
        );
    }

    public static void main(String[] args) {
        TaggingPipeline pipeline = getPipeline();
        Gson gson = new Gson();
        for (String line : FileUtils.getLineStream("eval/equity/dataset/AnnotatedTables-19092016/dataset_processed.json", "UTF-8")) {
            TruthTable table = gson.fromJson(line, TruthTable.class);
            pipeline.tag(table);
        }

        // TODO: write evaluate code

        System.exit(0);
    }
}
