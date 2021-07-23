package eval.qkbc.exp_1;

import eval.qkbc.QKBCResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import qkbc.text.RelationInstance;
import util.FileUtils;
import util.Gson;
import util.Number;

import java.io.IOException;
import java.util.HashMap;
import java.util.stream.Collectors;

public class AnnotationGenerator_LM {
    public static void generateTsvForGoogleSpreadsheet(String[] inputFiles,
                                                       String outputFile) {
        // load input (querying output)


        try {
            CSVPrinter csvPrinter = new CSVPrinter(FileUtils.getPrintWriter(outputFile, "UTF-8"), CSVFormat.DEFAULT
                    .withHeader("id", "settings", "--", "entity", "predicate", "value", "sentence", "groundtruth", "eval"));
            for (String i : inputFiles) {
                HashMap<String, RelationInstance> kbcId2RI = new HashMap<>();

                QKBCResult r = Gson.fromJson(FileUtils.getContent(i, "UTF-8"), QKBCResult.class);
                r.instances.stream().filter(ri -> ri.sampledEffectivePositiveIterIndices.size() > 0).forEach(ri -> {
                    kbcId2RI.put(ri.kbcId, ri);
                });

                kbcId2RI.values().stream().forEach(ri -> {
                    String qStr = "";
                    if (r.refinementByTime) {
                        qStr = "@" + ri.getYearCtx() + ": ";
                    }
                    if (ri.unit2TopRoBERTaValues == null) {
                        System.out.println(Gson.toJson(ri));
                    }
                    for (String u : ri.unit2TopRoBERTaValues.keySet()) {
                        qStr += (u + ": ");
                        qStr += ri.unit2TopRoBERTaValues.get(u).stream().map(v -> Number.getWrittenString(v, true))
                                .collect(Collectors.toList()).toString();
                        qStr += "; ";
                    }

                    try {
                        csvPrinter.printRecord(
                                ri.kbcId,
                                "roberta",
                                "",
                                ri.entity,
                                r.predicate,
                                qStr,
                                ri.getSentence(),
                                ri.groundtruth == null ? "" : ri.groundtruth,
                                ri.groundtruth == null && ri.eval == null ? "?" : ""
                        );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            csvPrinter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        // boostrapping
        generateTsvForGoogleSpreadsheet(
                new String[]{
                        "eval/qkbc/exp_1/qsearch_queries/lm_output_fact/building_height_lm.json",
                        "eval/qkbc/exp_1/qsearch_queries/lm_output_fact/mountain_elevation_lm.json",
                        "eval/qkbc/exp_1/qsearch_queries/lm_output_fact/river_length_lm.json",
                        "eval/qkbc/exp_1/qsearch_queries/lm_output_fact/stadium_capacity_lm.json",
                        "eval/qkbc/exp_1/qsearch_queries/lm_output_fact/city_altitude_lm.json",
                        "eval/qkbc/exp_1/qsearch_queries/lm_output_fact/company_revenue_lm.json"
                },
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/lm_annotation_gg.csv"
        );
    }
}
