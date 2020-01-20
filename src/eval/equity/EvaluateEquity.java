package eval.equity;

import com.google.gson.Gson;
import eval.TruthTable;
import pipeline.ColumnLinkFilteringNode;
import pipeline.DeepColumnScoringNode;
import pipeline.PostFilteringNode;
import pipeline.TaggingPipeline;
import pipeline.deep.DeepScoringClient;
import util.Constants;
import util.FileUtils;

public class EvaluateEquity {
    public static TaggingPipeline getPipeline() {
        return new TaggingPipeline(
                new DeepColumnScoringNode(
                        DeepColumnScoringNode.JOINT_INFERENCE,
                        new DeepScoringClient(true, -1),
//                        null,
                        1.5
                ),
                new ColumnLinkFilteringNode(0),
                new PostFilteringNode()
        );
    }

    public static void main(String[] args) {
        TaggingPipeline pipeline = getPipeline();
        Gson gson = new Gson();
        double microAvg = 0;
        int nGoodTable = 0;
        int nBadTable = 0;
        for (String line : FileUtils.getLineStream("eval/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation_linking_done.json", "UTF-8")) {
            TruthTable table = gson.fromJson(line, TruthTable.class);
            System.out.println("Original:");
            System.out.println(table.getTableContentPrintable(false, true, false));
            System.out.println("Ground Truth:");
            System.out.println(table.getTableContentPrintable(true, true, true));
            double precPrior = table.getEntityDisambiguationPrecisionFromFirstCandidate();

            pipeline.tag(table);

            double prec = table.getEntityDisambiguationPrecisionFromTarget();
            if (prec == -1 || precPrior == -1) {
                ++nBadTable;
                continue;
            }
            System.out.println(String.format("precEntityDisambiguation: Prior/Ours: %.2f/%.2f", precPrior * 100, prec * 100));
            System.out.println("====================================================================================================");
            ++nGoodTable;
            microAvg += prec;
        }
        System.out.println("nBadTable/nGoodTable: " + nBadTable + "/" + nGoodTable);
        System.out.println(String.format("Micro-Avg-Prec: %.2f", microAvg / nGoodTable * 100));

        System.exit(0);
    }
}
