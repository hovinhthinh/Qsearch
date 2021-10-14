package eval.qkbc.exp_1;

import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.standardize.Quantity;
import eval.qkbc.QKBCResult;
import eval.qkbc.WikidataGroundTruthExtractor;
import model.quantity.kg.KgUnit;
import nlp.NLP;
import nlp.Static;
import org.json.JSONObject;
import qkbc.text.RelationInstance;
import qkbc.text.RelationInstanceNoiseFilter;
import shaded.org.apache.http.client.utils.URIBuilder;
import util.Number;
import util.*;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;


public class GPT3LMBaseline {
    public static final String GPT3_END_POINT = "http://localhost:6993/complete";

    public static String surface(String entity) {
        return NLP.stripSentence(entity.substring(1, entity.length() - 1).replace('_', ' ')
                .replaceFirst("\\(.+\\)", ""));
    }

    public static String query(ArrayList<Pair<String, String>> examples, String question) {
        StringBuilder prompt = new StringBuilder();
        for (Pair<String, String> e : examples) {
            prompt.append("Q: ").append(e.first).append("\n")
                    .append("A: ").append(e.second).append("\n\n");
        }
        prompt.append("Q: ").append(question).append("\n").append("A:");

        try {
            URIBuilder b = new URIBuilder(GPT3_END_POINT);
            b.addParameter("prompt", prompt.toString());
            String resp = Crawler.getContentFromUrl(b.toString());
            return new JSONObject(resp).getJSONArray("choices").getJSONObject(0).getString("text").trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void downloadGptAnswers(String baseInputFile, int baseInputIter, ArrayList<Pair<String, String>> examples, String template, String outputFile) {
        QKBCResult r = Gson.fromJson(FileUtils.getContent(baseInputFile), QKBCResult.class);
        r.template = template;
        r.nIterations = 1;

        int cnt = 0;
        for (RelationInstance ri : r.instances) {
            ri.noiseIterIndices.clear();
            if (ri.effectivePositiveIterIndices.contains(baseInputIter)) {
                ++cnt;
                ri.effectivePositiveIterIndices.clear();
                ri.effectivePositiveIterIndices.add(1);
                String masked = template;
                masked = masked.replace("[ENTITY]", surface(ri.entity));
                if (template.contains("[TIME]")) {
                    masked = masked.replace("[TIME]", ri.getYearCtx());
                }
                ri.gpt3Output = query(examples, masked);
                System.out.println(cnt + ": " + masked + " => " + ri.gpt3Output);
            } else {
                ri.effectivePositiveIterIndices.clear();
            }
        }

        PrintWriter out = FileUtils.getPrintWriter(outputFile, "UTF-8");
        out.println(Gson.toJson(r));
        out.close();
    }


    public static void harvest(String gptInputFile, String groundTruthFile, String outputFile) {
        Map<String, WikidataGroundTruthExtractor.PredicateNumericalFact> groundTruth = groundTruthFile == null ? null :
                WikidataGroundTruthExtractor.loadPredicateGroundTruthFromFile(groundTruthFile).stream()
                        .collect(Collectors.toMap(f -> f.e, f -> f));

        QKBCResult r = Gson.fromJson(FileUtils.getContent(gptInputFile), QKBCResult.class);

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

                model.quantity.Quantity qt = null;
                try {
                    for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(ri.gpt3Output, true, null)) {
                        if (span.object instanceof Quantity) {
                            if (!model.quantity.Quantity.fixQuantityFromIllinois(span, ri.gpt3Output)) {
                                continue;
                            }
                            Quantity q = (Quantity) span.object;
                            qt = new model.quantity.Quantity(q.value, NLP.stripSentence(q.units), "=");
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                if (qt == null) {
                    throw new RuntimeException("qt is null");
                }

                double thisV = qt.value * qt.getScale();
                if (!qt.getDomain().equals(KgUnit.getKgUnitFromEntityName(f.quantities.get(0).second).getDomain())) {
                    System.out.println("wrong domain: " + ri.gpt3Output);
                    ri.groundtruth = false;
                    continue;
                }
                for (Pair<Double, String> p : f.quantities) {
                    double v = p.first * KgUnit.getKgUnitFromEntityName(p.second).conversionToSI;
                    if (Number.relativeNumericDistance(v, thisV) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                        ri.groundtruth = true;
                        continue loop;
                    }
                }
                ri.groundtruth = false;
            }
        }

        // mark sampled instances
        for (RelationInstance ri : r.instances) {
            ri.sampledNoiseIterIndices = new ArrayList<>();
            if (ri.sampledEffectivePositiveIterIndices.contains(1)) {
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
        downloadGptAnswers("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/building_height_ourN.json", 9,
                new ArrayList<>() {{
                    add(new Pair<>("how tall is Eiffel Tower?", "324 metres"));
                    add(new Pair<>("how tall is Burj Khalifa?", "2,717 feet"));
                    add(new Pair<>("how tall is Wuhan Greenland Center?", "636 m"));
                }},
                "how tall is [ENTITY]?",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/building_height_gpt_raw.json");
        harvest("./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/building_height_gpt_raw.json",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/building_height_gpt.json");


        downloadGptAnswers("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/mountain_elevation_ourN.json", 9,
                new ArrayList<>() {{
                    add(new Pair<>("what is the elevation of Mount Everest?", "8,848.86 m"));
                    add(new Pair<>("what is the elevation of Haystack Rock?", "235 feet"));
                    add(new Pair<>("what is the elevation of Midsummer Hill?", "284 metres"));
                }},
                "what is the elevation of [ENTITY]?",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/mountain_elevation_gpt_raw.json");
        harvest("./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/mountain_elevation_gpt_raw.json",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/mountain_elevation_gpt.json");


        downloadGptAnswers("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/stadium_capacity_ourN.json", 10,
                new ArrayList<>() {{
                    add(new Pair<>("what is the capacity of Old Trafford?", "74,140"));
                    add(new Pair<>("what is the capacity of Wembley Stadium?", "90,000"));
                    add(new Pair<>("what is the capacity of Melbourne Cricket Ground?", "100,024"));
                }},
                "what is the capacity of [ENTITY]?",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/stadium_capacity_gpt_raw.json");
        harvest("./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/stadium_capacity_gpt_raw.json",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/stadium_capacity_gpt.json");


        downloadGptAnswers("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/river_length_ourN.json", 9,
                new ArrayList<>() {{
                    add(new Pair<>("what is the length of Danube?", "2,850 km"));
                    add(new Pair<>("what is the length of River Thames?", "215 miles"));
                    add(new Pair<>("what is the length of Tapti River?", "700 km"));
                }},
                "what is the length of [ENTITY]?",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/river_length_gpt_raw.json");
        harvest("./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/river_length_gpt_raw.json",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/river_length_gpt.json");

        downloadGptAnswers("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/powerstation_capacity_ourN.json", 3,
                new ArrayList<>() {{
                    add(new Pair<>("what is the capacity of Dhekelia Power Station?", "460 MW"));
                    add(new Pair<>("what is the capacity of Nam Ngum Dam?", "155 MW"));
                    add(new Pair<>("what is the capacity of Boguchany Dam?", "2,997 MW"));
                }},
                "what is the capacity of [ENTITY]?",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/powerstation_capacity_gpt_raw.json");
        harvest("./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/powerstation_capacity_gpt_raw.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-powerStation_capacity",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/powerstation_capacity_gpt.json");


        downloadGptAnswers("eval/qkbc/exp_1/qsearch_queries/our_output_fact_new/earthquake_magnitude_ourN.json", 5,
                new ArrayList<>() {{
                    add(new Pair<>("what is the magnitude of 2016 Sumatra earthquake?", "7.8"));
                    add(new Pair<>("what is the magnitude of 1963 Kuril Islands earthquake?", "8.5"));
                    add(new Pair<>("what is the magnitude of 1987 Whittier Narrows earthquake?", "5.9"));
                }},
                "what is the magnitude of [ENTITY]?",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/earthquake_magnitude_gpt_raw.json");
        harvest("./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/earthquake_magnitude_gpt_raw.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-earthquake_magnitude",
                "./eval/qkbc/exp_1/qsearch_queries/gpt_output_fact_new/earthquake_magnitude_gpt.json");
    }
}
