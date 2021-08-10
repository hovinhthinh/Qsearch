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
import java.util.HashMap;

public class AnnotationGenerator_Qsearch {
    public static void generateTsvForGoogleSpreadsheet(String[] inputFiles,
                                                       String outputFile) {
        // load input (querying output)


        try {
            CSVPrinter csvPrinter = new CSVPrinter(FileUtils.getPrintWriter(outputFile, "UTF-8"), CSVFormat.DEFAULT
                    .withHeader("id", "settings", "source", "entity", "predicate", "value", "sentence", "groundtruth", "eval"));
            for (String i : inputFiles) {
                HashMap<String, RelationInstance> kbcId2RI = new HashMap<>();

                QKBCResult r = Gson.fromJson(FileUtils.getContent(i, "UTF-8"), QKBCResult.class);
                r.instances.stream().filter(ri -> ri.sampledEffectivePositiveIterIndices.size() > 0).forEach(ri -> {
                    kbcId2RI.put(ri.kbcId, ri);
                });

                kbcId2RI.values().stream().forEach(ri -> {
                    Quantity q = Quantity.fromQuantityString(ri.quantity);
                    String qStr = Number.getWrittenString(q.value, true);

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
                                "qsearch",
                                source,
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
                        "eval/qkbc/exp_1/qsearch_queries/qs_output_fact/building_height_qs.json",
                        "eval/qkbc/exp_1/qsearch_queries/qs_output_fact/mountain_elevation_qs.json",
                        "eval/qkbc/exp_1/qsearch_queries/qs_output_fact/river_length_qs.json",
                        "eval/qkbc/exp_1/qsearch_queries/qs_output_fact/stadium_capacity_qs.json",
                        "eval/qkbc/exp_1/qsearch_queries/qs_output_fact/city_altitude_qs.json",
//                        "eval/qkbc/exp_1/qsearch_queries/qs_output_fact/company_revenue_qs_gt.json"
//                        "eval/qkbc/exp_1/qsearch_queries/qs_output_fact/earthquake_magnitude_qs.json",
//                        "eval/qkbc/exp_1/qsearch_queries/qs_output_fact/powerStation_capacity_qs.json",

                },
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qs_annotation_gg.csv"
        );
    }
}
