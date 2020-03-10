package eval;

import com.google.gson.Gson;
import pipeline.ColumnLinkFilteringNode;
import pipeline.PostFilteringNode;
import pipeline.TaggingPipeline;
import pipeline.TextBasedColumnScoringNode;
import util.FileUtils;
import util.MetricReporter;
import util.Pair;
import yago.QfactTaxonomyGraph;

import java.util.Scanner;

public class Evaluate {

    public static TaggingPipeline globalPipeline;
    public static TextBasedColumnScoringNode columnScoringNode;

    static {
        TextBasedColumnScoringNode.JOINT_MAX_NUM_COLUMN_LINKING = -1;
        columnScoringNode = TextBasedColumnScoringNode.getJointInferenceInstance();
        globalPipeline = new TaggingPipeline(
                columnScoringNode,
                new ColumnLinkFilteringNode(0),
                new PostFilteringNode()
        );
    }

    public static TaggingPipeline getPipeline(double jointWeight) {
        columnScoringNode.homogeneityWeight = jointWeight;
        columnScoringNode.inferenceMode = jointWeight == 1
                ? TextBasedColumnScoringNode.INDEPENDENT_INFERENCE
                : TextBasedColumnScoringNode.JOINT_INFERENCE;
        return globalPipeline;
    }


    // args: INTERACTIVE <inputFile>
    public static void main(String[] args) {
        if (args.length == 2 && args[0].equals("INTERACTIVE")) {
            Scanner in = new Scanner(System.in);
            String configStr;
            System.out.println("__ready_to_evaluate__");
            while ((configStr = in.nextLine()) != null) {
                String resultJson = evaluateWithConfig(args[1], configStr);
                System.out.println(String.format("__interactive_result__\t%s", resultJson));
                System.out.flush();
            }
            in.close();
        } else {
            TextBasedColumnScoringNode.JOINT_MAX_NUM_COLUMN_LINKING = -1;
            String inputFile = "eval/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation_linking.json";
            columnScoringNode.inferenceMode = TextBasedColumnScoringNode.INDEPENDENT_INFERENCE;
            columnScoringNode.homogeneityWeight = 1;
            evaluateWithConfig(inputFile, null);
        }
    }

    // configStr: "Joint_Weight",
    //       "H_Prior_Weight",
    //       "H_Cooccur_Weight",
    //       "H_Context_Weight",
    //       "H_Agree_Weight",
    //       "L_nTop_Related",
    //       "L_Context_Weight",
    //       "L_Type_Penalty",
    // space separated
    public static String evaluateWithConfig(String inputFile, String configStr) {
        TaggingPipeline pipeline = globalPipeline;

        // USE BATCH
        if (configStr != null) {
            System.out.println("Config: " + configStr);
            String[] args = configStr.trim().split("\\s++");
            double jointWeight = Double.parseDouble(args[0]);
            TextBasedColumnScoringNode.PRIOR_WEIGHT = Double.parseDouble(args[1]);
            TextBasedColumnScoringNode.COOCCUR_WEIGHT = Double.parseDouble(args[2]);
            TextBasedColumnScoringNode.CONTEXT_WEIGHT = Double.parseDouble(args[3]);
            /* TextBasedColumnScoringNode.AGREE_WEIGHT = Double.parseDouble(args[4]); // but not used */

            QfactTaxonomyGraph.NTOP_RELATED_ENTITY = Integer.parseInt(args[5]);
            QfactTaxonomyGraph.QFACT_CONTEXT_MATCH_WEIGHT = Double.parseDouble(args[6]);
            QfactTaxonomyGraph.TYPE_RELATED_PENALTY_WEIGHT = Double.parseDouble(args[7]);
            pipeline = getPipeline(jointWeight);
        }

        Gson gson = new Gson();
        int nGoodTable = 0, nBadTable = 0;

        MetricReporter reporter = new MetricReporter(Evaluate.class.getName());

        for (String line : FileUtils.getLineStream(inputFile, "UTF-8")) {
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
            double precCAFirstEntityColumn = table.getAlignmentPrecisionFromFirstEntityColumn();
            double precCAMostUniqueColumn = table.getAlignmentPrecisionFromMostUniqueColumnFromTheLeft();
            double precCAMostUniqueEntityColumn = table.getAlignmentPrecisionFromMostUniqueEntityColumnFromTheLeft();

            pipeline.tag(table);
            int nECols = 0;
            for (int i = 0; i < table.nColumn; ++i) {
                if (table.isEntityColumn[i]) {
                    ++nECols;
                }
            }

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
                            String e = table.data[r][table.quantityToEntityColumn[i]].getRepresentativeEntityLink().target;
                            e = "<" + e.substring(e.lastIndexOf(":") + 1) + ">";
                            System.out.println("   " + e + " --> " + table.QfactMatchingStr[r][i]);
                        } else {
                            System.out.println("   null");
                        }
                    }
                }
            }
            System.out.println(String.format("precEntityDisambiguation: Prior/Ours: %.2f/%.2f", precEDPrior * 100, precEDOurs * 100));
            System.out.println(String.format("precColumnAlignment: FirstColumn/FirstEntityColumn/Ours: %.2f/%.2f/%.2f", precCAFirstColumn * 100, precCAFirstEntityColumn * 100, precCAOurs * 100));
//            System.out.println(String.format("qtFoundRateInText: %.2f", qtFoundRate));
            System.out.println("========================================================================================================================================================================================================");
            ++nGoodTable;

            reporter.recordAverage("%TablesWithOneEntityColumn", nECols == 1 ? 1 : 0);
            reporter.recordAverage("macroPrecEDPrior", precEDPrior);
            reporter.recordAverage("macroPrecEDOurs", precEDOurs);
            reporter.recordAverage("macroPrecCAFirstColumn", precCAFirstColumn);
            reporter.recordAverage("macroPrecCAFirstEntityColumn", precCAFirstEntityColumn);
            reporter.recordAverage("macroPrecCAMostUniqueColumn", precCAMostUniqueColumn);
            reporter.recordAverage("macroPrecCAMostUniqueEntityColumn", precCAMostUniqueEntityColumn);
            reporter.recordAverage("macroPrecCAOurs", precCAOurs);

            reporter.recordMicroAverage("microPrecEDPrior", precEDPriorInfo);
            reporter.recordMicroAverage("microPrecEDOurs", precEDOursInfo);
        }
        System.out.println("nBadTable/nGoodTable: " + nBadTable + "/" + nGoodTable);
        System.out.println(reporter.getReportString());
        return reporter.getReportJsonString();
    }
}