package eval.equity;

import com.google.gson.Gson;
import eval.TruthTable;
import pipeline.ColumnLinkFilteringNode;
import pipeline.DeepColumnScoringNode;
import pipeline.PostFilteringNode;
import pipeline.TaggingPipeline;
import pipeline.deep.DeepScoringClient;
import util.FileUtils;

public class EvaluateEquity {
    public static TaggingPipeline getPipeline() {
        return new TaggingPipeline(
                new DeepColumnScoringNode(
                        DeepColumnScoringNode.JOINT_INFERENCE,
                        new DeepScoringClient(true, -1),
                        1.5
                ),
                new ColumnLinkFilteringNode(0),
                new PostFilteringNode()
        );
    }

    public static void main(String[] args) {
        TaggingPipeline pipeline = getPipeline();
        Gson gson = new Gson();
        double microAvgED = 0;
        double microAvgCA = 0;
        int nGoodTable = 0;
        int nBadTable = 0;
        for (String line : FileUtils.getLineStream("eval/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation_linking.json", "UTF-8")) {
            TruthTable table = gson.fromJson(line, TruthTable.class);
            System.out.println("Original:");
            System.out.println(table.getTableContentPrintable(false, true, false));
            System.out.println("Ground Truth:");
            System.out.println(table.getTableContentPrintable(true, true, true));
            double precEDPrior = table.getEntityDisambiguationPrecisionFromFirstCandidate();

            pipeline.tag(table);

            double precED = table.getEntityDisambiguationPrecisionFromTarget();
            if (precED == -1 || precEDPrior == -1) {
                ++nBadTable;
                continue;
            }
            double precCA = table.getEntityDisambiguationPrecisionFromTarget();
            if (precCA == -1) {
                ++nBadTable;
                continue;
            }

            System.out.println(String.format("precEntityDisambiguation: Prior/Ours: %.2f/%.2f", precEDPrior * 100, precED * 100));
            System.out.println(String.format("precColumnAlignment: Ours: %.2f", precCA * 100));
            System.out.println("====================================================================================================");
            ++nGoodTable;
            microAvgED += precED;
            microAvgCA += precCA;
        }
        System.out.println("nBadTable/nGoodTable: " + nBadTable + "/" + nGoodTable);
        System.out.println(String.format("Micro-Avg-PrecED: %.2f", microAvgED / nGoodTable * 100));
        System.out.println(String.format("Micro-Avg-PrecED: %.2f", microAvgCA / nGoodTable * 100));

        System.exit(0);
    }
}
