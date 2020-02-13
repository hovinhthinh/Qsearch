package eval.equity;

import com.google.gson.Gson;
import eval.TruthTable;
import pipeline.ColumnLinkFilteringNode;
import pipeline.PostFilteringNode;
import pipeline.TaggingPipeline;
import pipeline.TextBasedColumnScoringNode;
import util.FileUtils;
import util.MetricReporter;
import util.Pair;

public class EvaluateEquity {
    public static TaggingPipeline getPipeline() {
        return new TaggingPipeline(
                new TextBasedColumnScoringNode(
                        TextBasedColumnScoringNode.JOINT_INFERENCE,
                        0
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
//            table.linkQuantitiesInTableAndText();
//            double qtFoundRate = table.getRateOfTableQuantitiesFoundInText();
//            reporter.recordAverage("rateQtFoundRateInText", qtFoundRate);
//            reporter.recordAverage("macroPrecCAHeaderEmbedding", table.getAlignmentPrecisionFromHeaderEmbedding());
//            reporter.recordAverage("macroPrecCAColumnEmbedding", table.getAlignmentPrecisionFromColumnEmbedding());
//            reporter.recordAverage("macroPrecCAColumnJaccardIndex", table.getAlignmentPrecisionFromColumnJaccardIndex());

            System.out.println("--- Original ---");
            System.out.println("- URL: " + table.source);
            System.out.println("- Title: " + table.pageTitle);
//            System.out.println("- Content: \r\n" + table.surroundingTextAsParagraph.toStringWithNewlineAfterSentences());
            System.out.println("- Caption: " + table.caption);
            System.out.println(table.getTableContentPrintable(false, true, false, false));
            System.out.println("--- Ground Truth ---");
            System.out.println(table.getTableContentPrintable(true, true, true, true));

            double precEDPrior = table.getEntityDisambiguationPrecisionFromPrior();
            Pair<Integer, Integer> precEDPriorInfo = table.getEntityDisambiguationMicroPrecisionInfoFromPrior();
            System.out.println("--- Prior ---");
            System.out.println(table.getTableContentPrintable(true, true, true, true));
            double precCAFirstColumn = table.getAlignmentPrecisionFromFirstColumn();

            pipeline.tag(table);

            double precEDOurs = table.getEntityDisambiguationPrecisionFromTarget();
            Pair<Integer, Integer> precEDOursInfo = table.getEntityDisambiguationMicroPrecisionInfoFromTarget();

            double precCAOurs = table.getAlignmentPrecisionFromTarget();

//            if (precEDOurs == -1 || precEDPrior == -1 || precCAOurs == -1 || precCAFirstColumn == -1 || qtFoundRate == -1) {
//                ++nBadTable;
//                continue;
//            }

            System.out.println("--- Ours ---");
            System.out.println(table.getTableContentPrintable(true, true, true, true));

            System.out.println("Linked Qfacts:");
            for (int i = 0; i < table.nColumn; ++i) {
                if (table.isNumericColumn[i]) {
                    System.out.println("+ Column " + i + " --> " + table.quantityToEntityColumn[i] + ":");
                    for (int r = 0; r < table.nDataRow; ++r) {
                        if (table.QfactMatchingStr[r][i] != null) {
                            System.out.println("   " + table.QfactMatchingStr[r][i]);
                        } else {
                            System.out.println("   null");
                        }
                    }
                }
            }
            System.out.println(String.format("precEntit                                matchStr = lastHeaderResult.second;yDisambiguation: Prior/Ours: %.2f/%.2f", precEDPrior * 100, precEDOurs * 100));
            System.out.println(String.format("precColumnAlignment: FirstColumn/Ours: %.2f/%.2f", precCAFirstColumn * 100, precCAOurs * 100));
//            System.out.println(String.format("qtFoundRateInText: %.2f", qtFoundRate));
            System.out.println("========================================================================================================================================================================================================");
            ++nGoodTable;

            reporter.recordAverage("macroPrecEDPrior", precEDPrior);
            reporter.recordAverage("macroPrecEDOurs", precEDOurs);
            reporter.recordAverage("macroPrecCAFirstColumn", precCAFirstColumn);
            reporter.recordAverage("macroPrecCAOurs", precCAOurs);

            reporter.recordMicroAverage("microPrecEDPrior", precEDPriorInfo);
            reporter.recordMicroAverage("microPrecEDOurs", precEDOursInfo);
        }
        System.out.println("nBadTable/nGoodTable: " + nBadTable + "/" + nGoodTable);
        System.out.println(reporter.getReportString());

        System.exit(0);
    }
}
