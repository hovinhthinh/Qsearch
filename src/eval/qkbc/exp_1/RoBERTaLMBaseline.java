package eval.qkbc.exp_1;

import eval.qkbc.QKBCResult;
import eval.qkbc.WikidataGroundTruthExtractor;
import model.quantity.kg.KgUnit;
import nlp.NLP;
import org.json.JSONArray;
import qkbc.text.RelationInstance;
import qkbc.text.RelationInstanceNoiseFilter;
import shaded.org.apache.http.client.utils.URIBuilder;
import util.Number;
import util.*;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;


public class RoBERTaLMBaseline {
    public static final String ROBERTA_END_POINT = "http://varuna:12993/unmask";

    public static String surface(String entity) {
        return NLP.stripSentence(entity.substring(1, entity.length() - 1).replace('_', ' ')
                .replaceFirst("\\(.+\\)", ""));
    }

    public static void harvest(String baseInputFile, int baseInputIter, String template,
                               List<Pair<String, String>> possibleUnits,
                               String groundTruthFile, String outputFile) {
        Map<String, WikidataGroundTruthExtractor.PredicateNumericalFact> groundTruth = groundTruthFile == null ? null :
                WikidataGroundTruthExtractor.loadPredicateGroundTruthFromFile(groundTruthFile).stream()
                        .collect(Collectors.toMap(f -> f.e, f -> f));


        QKBCResult r = Gson.fromJson(FileUtils.getContent(baseInputFile), QKBCResult.class);
        r.template = template;
        r.nIterations = 1;

        for (RelationInstance ri : r.instances) {
            ri.noiseIterIndices.clear();
            if (ri.effectivePositiveIterIndices.contains(baseInputIter)) {
                ri.effectivePositiveIterIndices.clear();
                ri.effectivePositiveIterIndices.add(1);
                String masked = template;
                masked = masked.replace("[ENTITY]", surface(ri.entity));
                if (template.contains("[TIME]")) {
                    masked = masked.replace("[TIME]", ri.getYearCtx());
                }
                ri.unit2TopRoBERTaValues = new HashMap<>();
                for (Pair<String, String> u : possibleUnits) {
                    ArrayList<String> values = new ArrayList<>();
                    String input = NLP.stripSentence(masked.replace("[QUANTITY]", u.second));
                    try {

                        URIBuilder b = new URIBuilder(ROBERTA_END_POINT);
                        b.addParameter("input", input);
                        JSONArray o = new JSONArray(Crawler.getContentFromUrl(b.toString()));
                        for (int i = 0; i < o.length(); ++i) {
                            values.add(o.getJSONObject(i).getString("token_str").trim());
                        }

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    ArrayList<Double> parsedValues = new ArrayList<>();
                    for (String v : values) {
                        try {
                            double pV = Double.parseDouble(v.replaceAll(",", ""));
                            if (u.second.contains("billion")) {
                                pV *= 1e9;
                            } else if (u.second.contains("million")) {
                                pV *= 1e6;
                            }
                            parsedValues.add(pV);
                        } catch (Exception e) {
                        }
                    }
                    System.out.println(">> " + input + " >> " + values + " >> " + parsedValues);
                    if (!ri.unit2TopRoBERTaValues.containsKey(u.first)) {
                        ri.unit2TopRoBERTaValues.put(u.first, parsedValues);
                    } else {
                        ri.unit2TopRoBERTaValues.get(u.first).addAll(parsedValues);
                    }
                }
            } else {
                ri.effectivePositiveIterIndices.clear();
            }
        }
        // mark  groundtruth
        if (groundTruth != null) {
            loop:
            for (RelationInstance ri : r.instances) {
                ri.groundtruth = null;
                if (ri.effectivePositiveIterIndices.size() == 0) {
                    continue;
                }
                WikidataGroundTruthExtractor.PredicateNumericalFact f = groundTruth.get(ri.entity);
                if (f == null) {
                    continue;
                }
                for (String unit : ri.unit2TopRoBERTaValues.keySet()) {
                    for (Double value : ri.unit2TopRoBERTaValues.get(unit)) {
                        double thisV = value * KgUnit.getKgUnitFromEntityName(unit).conversionToSI;
                        for (Pair<Double, String> p : f.quantities) {
                            double v = p.first * KgUnit.getKgUnitFromEntityName(p.second).conversionToSI;
                            if (Number.relativeNumericDistance(v, thisV) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                                ri.groundtruth = true;
                                continue loop;
                            }
                        }
                    }
                }
                ri.groundtruth = false;
            }
        }

        // mark sampled instances
        for (RelationInstance ri : r.instances) {
            ri.sampledNoiseIterIndices = new ArrayList<>();
            if (ri.sampledEffectivePositiveIterIndices.contains(baseInputIter)) {
                ri.sampledEffectivePositiveIterIndices.clear();
                ri.sampledEffectivePositiveIterIndices.add(1);
            } else {
                ri.sampledEffectivePositiveIterIndices.clear();
            }
        }

        PrintWriter out = FileUtils.getPrintWriter(outputFile, "UTF-8");
        out.println(Gson.toJson(r));
        out.close();
    }

    public static void main(String[] args) {
        harvest("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/building_height_ourN.json", 9,
                "[ENTITY] has a height of [QUANTITY] .",
                Arrays.asList(new Pair<>("<Metre>", "<mask> metres"), new Pair<>("<Foot_(unit)>", "<mask> feet")),
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height",
                "./eval/qkbc/exp_1/qsearch_queries/lm_output_fact_new/building_height_lm.json");

        harvest("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/mountain_elevation_ourN.json", 9,
                "[ENTITY] is [QUANTITY] high .",
                Arrays.asList(new Pair<>("<Metre>", "<mask> metres"), new Pair<>("<Foot_(unit)>", "<mask> feet")),
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation",
                "./eval/qkbc/exp_1/qsearch_queries/lm_output_fact_new/mountain_elevation_lm.json");

        harvest("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/stadium_capacity_ourN.json", 10,
                "[ENTITY] has a capacity of [QUANTITY] .",
                Arrays.asList(new Pair<>("", "<mask>")),
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity",
                "./eval/qkbc/exp_1/qsearch_queries/lm_output_fact_new/stadium_capacity_lm.json");

        harvest("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/river_length_ourN.json", 9,
                "[ENTITY] is [QUANTITY] long .",
                Arrays.asList(new Pair<>("<Kilometre>", "<mask> kilometers"), new Pair<>("<Mile>", "<mask> miles")),
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length",
                "./eval/qkbc/exp_1/qsearch_queries/lm_output_fact_new/river_length_lm.json");

//        harvest("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/company_revenue_ourN.json", 4,
//                "[ENTITY] reported [QUANTITY] in revenue in [TIME] .",
//                Arrays.asList(new Pair<>("<United_States_dollar>", "$ <mask> billion"),
//                        new Pair<>("<United_States_dollar>", "$ <mask> million"),
//                        new Pair<>("<Euro>", "<mask> billion euros"),
//                        new Pair<>("<Euro>", "<mask> million euros")),
//                null,
//                "./eval/qkbc/exp_1/qsearch_queries/lm_output_fact_new/company_revenue_lm.json");

        harvest("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/powerstation_capacity_ourN.json", 3,
                "[ENTITY] has a capacity of [QUANTITY] .",
                Arrays.asList(new Pair<>("<megawatt_wd:Q6982035>", "<mask> MW"),
                        new Pair<>("<Kilowatt>", "<mask> kW")),
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-powerStation_capacity",
                "./eval/qkbc/exp_1/qsearch_queries/lm_output_fact_new/powerStation_capacity_lm.json");

        harvest("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/earthquake_magnitude_ourN.json", 5,
                "[ENTITY] had a magnitude of [QUANTITY] .",
                Arrays.asList(new Pair<>("", "<mask>")),
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-earthquake_magnitude",
                "./eval/qkbc/exp_1/qsearch_queries/lm_output_fact_new/earthquake_magnitude_lm.json");
    }
}
