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
        double microAvgEDOurs = 0, microAvgCAOurs = 0;
        double microAvgEDPrior = 0, microAvgCAFirstColumn = 0;
        double microAvgQtFoundRateInText = 0;
        int nGoodTable = 0;
        int nBadTable = 0;
        for (String line : FileUtils.getLineStream("eval/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation_linking.json", "UTF-8")) {
            TruthTable table = gson.fromJson(line, TruthTable.class);
            table.linkQuantitiesInTableAndText();
            double qtFoundRate = table.getRateOfTableQuantitiesFoundInText();

            System.out.println("=== Original ===");
            System.out.println("- URL: " + table.source);
            System.out.println("- Title: " + table.pageTitle);
            System.out.println("- Content: \r\n" + table.surroundingTextAsParagraph.toStringWithNewlineAfterSentences());
            System.out.println("- Caption: " + table.caption);
            System.out.println(table.getTableContentPrintable(false, true, false));
            System.out.println("=== Ground Truth ===");
            System.out.println(table.getTableContentPrintable(true, true, true));
            double precEDPrior = table.getEntityDisambiguationPrecisionFromPrior();
            double precCAFirstColumn = table.getAlignmentPrecisionFromFirstColumn();

            pipeline.tag(table);

            double precEDOurs = table.getEntityDisambiguationPrecisionFromTarget();
            double precCAOurs = table.getAlignmentPrecisionFromTarget();

            if (precEDOurs == -1 || precEDPrior == -1 || precCAOurs == -1 || precCAFirstColumn == -1 || qtFoundRate == -1) {
                ++nBadTable;
                continue;
            }

            System.out.println(String.format("precEntityDisambiguation: Prior/Ours: %.2f/%.2f", precEDPrior * 100, precEDOurs * 100));
            System.out.println(String.format("precColumnAlignment: FirstColumn/Ours: %.2f/%.2f", precCAFirstColumn * 100, precCAOurs * 100));
            System.out.println(String.format("qtFoundRateInText: %.2f", qtFoundRate));
            System.out.println("========================================================================================================================================================================================================");
            ++nGoodTable;
            microAvgEDOurs += precEDOurs;
            microAvgCAOurs += precCAOurs;
            microAvgEDPrior += precEDPrior;
            microAvgCAFirstColumn += precCAFirstColumn;
            microAvgQtFoundRateInText += qtFoundRate;
        }
        System.out.println("nBadTable/nGoodTable: " + nBadTable + "/" + nGoodTable);
        System.out.println(String.format("Micro-Avg-PrecED: Prior/Ours: %.2f/%.2f", microAvgEDPrior / nGoodTable * 100, microAvgEDOurs / nGoodTable * 100));
        System.out.println(String.format("Micro-Avg-PrecCA: FirstColumn/Ours: %.2f/%.2f", microAvgCAFirstColumn / nGoodTable * 100, microAvgCAOurs / nGoodTable * 100));
        System.out.println(String.format("Micro-Avg-QtFoundRateInText: %.2f", microAvgQtFoundRateInText / nGoodTable * 100));

        System.exit(0);
    }
}
