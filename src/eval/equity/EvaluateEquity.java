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
import util.MetricReporter;

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
        int nGoodTable = 0, nBadTable = 0;

        MetricReporter reporter = new MetricReporter(EvaluateEquity.class.getName());

        for (String line : FileUtils.getLineStream("eval/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation_linking.json", "UTF-8")) {
            TruthTable table = gson.fromJson(line, TruthTable.class);
            table.linkQuantitiesInTableAndText();
            double qtFoundRate = table.getRateOfTableQuantitiesFoundInText();
            reporter.recordAverage("microPrecCAHeaderEmbedding", table.getAlignmentPrecisionFromHeaderEmbedding());
            reporter.recordAverage("microPrecCAColumnEmbedding", table.getAlignmentPrecisionFromColumnEmbedding());
            reporter.recordAverage("microPrecCAColumnJaccardIndex", table.getAlignmentPrecisionFromColumnJaccardIndex());

            System.out.println("--- Original ---");
            System.out.println("- URL: " + table.source);
            System.out.println("- Title: " + table.pageTitle);
            System.out.println("- Content: \r\n" + table.surroundingTextAsParagraph.toStringWithNewlineAfterSentences());
            System.out.println("- Caption: " + table.caption);
            System.out.println(table.getTableContentPrintable(false, true, false));
            System.out.println("--- Ground Truth ---");
            System.out.println(table.getTableContentPrintable(true, true, true));
            double precEDPrior = table.getEntityDisambiguationPrecisionFromPrior();
            double precCAFirstColumn = table.getAlignmentPrecisionFromFirstColumn();

            pipeline.tag(table);

            double precEDOurs = table.getEntityDisambiguationPrecisionFromTarget();
            double precCAOurs = table.getAlignmentPrecisionFromTarget();
//            double precEDOurs = 0;
//            double precCAOurs = 0;

            if (precEDOurs == -1 || precEDPrior == -1 || precCAOurs == -1 || precCAFirstColumn == -1 || qtFoundRate == -1) {
                ++nBadTable;
                continue;
            }

            System.out.println("--- Ours ---");
            System.out.println(table.getTableContentPrintable(true, true, true));

            System.out.println(String.format("precEntityDisambiguation: Prior/Ours: %.2f/%.2f", precEDPrior * 100, precEDOurs * 100));
            System.out.println(String.format("precColumnAlignment: FirstColumn/Ours: %.2f/%.2f", precCAFirstColumn * 100, precCAOurs * 100));
            System.out.println(String.format("qtFoundRateInText: %.2f", qtFoundRate));
            System.out.println("========================================================================================================================================================================================================");
            ++nGoodTable;

            reporter.recordAverage("microPrecEDPrior", precEDPrior);
            reporter.recordAverage("microPrecEDOurs", precEDOurs);
            reporter.recordAverage("microPrecCAFirstColumn", precCAFirstColumn);
            reporter.recordAverage("microPrecCAOurs", precCAOurs);
            reporter.recordAverage("rateQtFoundRateInText", qtFoundRate);
        }
        System.out.println("nBadTable/nGoodTable: " + nBadTable + "/" + nGoodTable);
        System.out.println(reporter.getReportString());

        System.exit(0);
    }
}
