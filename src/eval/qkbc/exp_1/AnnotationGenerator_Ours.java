package eval.qkbc.exp_1;

import eval.qkbc.QKBCResult;
import model.quantity.Quantity;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import qkbc.text.RelationInstance;
import util.FileUtils;
import util.Gson;
import util.Number;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class AnnotationGenerator_Ours {
    public static void generateTsvForGoogleSpreadsheet(String inputFileNonParametric, String outputFile) {
        // load input (querying output)
        QKBCResult rn = Gson.fromJson(FileUtils.getContent(inputFileNonParametric, "UTF-8"), QKBCResult.class);

        Map<String, RelationInstance> kbcId2RI = new HashMap<>();
        Map<String, String> kbcId2Settings = new HashMap<>();
        Map<String, Integer> kbcId2IterP = new HashMap<>(), kbcId2IterN = new HashMap<>();
        rn.instances.stream().filter(ri -> ri.sampledEffectivePositiveIterIndices.size() > 0).forEach(ri -> {
            kbcId2IterN.put(ri.kbcId, ri.sampledEffectivePositiveIterIndices.get(0));
            kbcId2RI.put(ri.kbcId, ri);
            kbcId2Settings.put(ri.kbcId, kbcId2Settings.getOrDefault(ri.kbcId, "") + "N(" + ri.sampledEffectivePositiveIterIndices.get(0) + ")");
        });

        try {
            CSVPrinter csvPrinter = new CSVPrinter(FileUtils.getPrintWriter(outputFile, "UTF-8"), CSVFormat.DEFAULT
                    .withHeader("id", "settings", "iter", "source", "entity", rn.predicate, "sentence", "groundtruth", "eval"));

            kbcId2RI.values().stream().sorted(Comparator.comparing(ri ->
                    Math.min(kbcId2IterP.getOrDefault(ri.kbcId, 100), kbcId2IterN.getOrDefault(ri.kbcId, 100))
            )).forEach(ri -> {
                Quantity q = Quantity.fromQuantityString(ri.quantity);
                String qStr = Number.getWrittenString(q.value, true);
                if (rn.refinementByTime) {
                    qStr = "@" + ri.getYearCtx() + ": " + qStr;
                }

                String entityStr = q.getKgUnit().entity;
                if (entityStr != null) {
                    qStr += " " + entityStr;
                }

                String source = ri.getSource();
                source = source.substring(source.indexOf(":") + 1);
                source = source.substring(source.indexOf(":") + 1);

                try {
                    csvPrinter.printRecord(
                            ri.kbcId,
                            kbcId2Settings.get(ri.kbcId),
                            Math.min(kbcId2IterP.getOrDefault(ri.kbcId, 100), kbcId2IterN.getOrDefault(ri.kbcId, 100)),
                            source,
                            ri.entity,
                            qStr,
                            ri.getSentence(),
                            ri.groundtruth == null ? "" : ri.groundtruth,
                            ri.groundtruth == null && ri.eval == null ? "?" : ""
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            csvPrinter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/building_height_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/building_height_our.annotation_gg.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/mountain_elevation_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/mountain_elevation_our.annotation_gg.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/river_length_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/river_length_our.annotation_gg.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/stadium_capacity_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/stadium_capacity_our.annotation_gg.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/our_output_fact/company_revenue_ourN_gt.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/company_revenue_our.annotation_gg_gt.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/our_output_fact/powerstation_capacity_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/powerstation_capacity_our.annotation_gg_gt.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/our_output_fact/earthquake_magnitude_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/earthquake_magnitude_our.annotation_gg_gt.csv"
        );
    }
}
