package eval.qkbc;

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

public class AnnotationGenerator {
    public static void generateTsvForGoogleSpreadsheet(String inputFileParametric,
                                                       String inputFileNonParametric,
                                                       String groundTruthFile, boolean refinementByTime, String outputFile) {
        // load input (querying output)
        QKBCResult rp = Gson.fromJson(FileUtils.getContent(inputFileParametric, "UTF-8"), QKBCResult.class);
        QKBCResult rn = Gson.fromJson(FileUtils.getContent(inputFileNonParametric, "UTF-8"), QKBCResult.class);

        // load groundTruth
        Map<String, WikidataGroundTruthExtractor.PredicateNumericalFact> groundTruth = null;
        if (groundTruthFile != null) {
            groundTruth = new HashMap<>();
            for (String line : FileUtils.getLineStream(groundTruthFile)) {
                WikidataGroundTruthExtractor.PredicateNumericalFact f = Gson.fromJson(line, WikidataGroundTruthExtractor.PredicateNumericalFact.class);
                groundTruth.put(f.e, f);
            }
        }

        QKBCResult.markEffectiveAndGroundTruthFacts(rp, groundTruth, refinementByTime);
        QKBCResult.markEffectiveAndGroundTruthFacts(rn, groundTruth, refinementByTime);

        Map<String, RelationInstance> kbcId2RI = new HashMap<>();
        Map<String, String> kbcId2Settings = new HashMap<>();
        Map<String, Integer> kbcId2IterP = new HashMap<>(), kbcId2IterN = new HashMap<>();
        rp.instances.stream().filter(ri -> ri.effectivePositiveIterIndices.size() > 0).forEach(ri -> {
            kbcId2IterP.put(ri.kbcId, ri.effectivePositiveIterIndices.get(0));
            kbcId2RI.put(ri.kbcId, ri);
            kbcId2Settings.put(ri.kbcId, kbcId2Settings.getOrDefault(ri.kbcId, "") + "P(" + ri.effectivePositiveIterIndices.get(0) + ")");
        });
        rn.instances.stream().filter(ri -> ri.effectivePositiveIterIndices.size() > 0).forEach(ri -> {
            kbcId2IterN.put(ri.kbcId, ri.effectivePositiveIterIndices.get(0));
            kbcId2RI.put(ri.kbcId, ri);
            kbcId2Settings.put(ri.kbcId, kbcId2Settings.getOrDefault(ri.kbcId, "") + "N(" + ri.effectivePositiveIterIndices.get(0) + ")");
        });

        try {
            CSVPrinter csvPrinter = new CSVPrinter(FileUtils.getPrintWriter(outputFile, "UTF-8"), CSVFormat.DEFAULT
                    .withHeader("id", "settings", "iter", "source", "entity", rp.predicate, "sentence", "groundtruth", "eval"));

            kbcId2RI.values().stream().filter(ri -> ri.effectivePositiveIterIndices.size() > 0)
                    .sorted(Comparator.comparing(ri -> ri.effectivePositiveIterIndices.get(0)))
                    .forEach(ri -> {
                        Quantity q = Quantity.fromQuantityString(ri.quantity);
                        String qStr = Number.getWrittenString(q.value, true);
                        if (refinementByTime) {
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
                                    ri.eval == null ? "" : (ri.eval ? "TRUE" : "FALSE"),
                                    ri.eval == null ? "?" : ""
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
        // boostrapping
        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/building_height_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/building_height_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height", false,
                "eval/qkbc/exp_1/qsearch_queries/annotation/building_height_our.annotation_gg.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/mountain_elevation_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/mountain_elevation_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation", false,
                "eval/qkbc/exp_1/qsearch_queries/annotation/mountain_elevation_our.annotation_gg.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/river_length_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/river_length_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length", false,
                "eval/qkbc/exp_1/qsearch_queries/annotation/river_length_our.annotation_gg.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/stadium_capacity_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/stadium_capacity_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity", false,
                "eval/qkbc/exp_1/qsearch_queries/annotation/stadium_capacity_our.annotation_gg.csv"
        );

        // original
        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/city_altitude_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/city_altitude_ourN.json",
                null, false,
                "eval/qkbc/exp_1/qsearch_queries/annotation/city_altitude_our.annotation_gg.csv"
        );

        generateTsvForGoogleSpreadsheet("eval/qkbc/exp_1/qsearch_queries/company_revenue_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/company_revenue_ourN.json",
                null, true,
                "eval/qkbc/exp_1/qsearch_queries/annotation/company_revenue_our.annotation_gg.csv"
        );
    }
}
