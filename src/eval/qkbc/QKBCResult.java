package eval.qkbc;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import qkbc.text.RelationInstance;
import util.FileUtils;
import util.Gson;

import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

public class QKBCResult {
    public static final int ANNOTATION_SAMPLING_SIZE = 30;

    public String predicate;
    public boolean refinementByTime;
    public Integer groundTruthSize;
    public ArrayList<String> ctxList;
    public int nIterations;
    public ArrayList<RelationInstance> instances;

    // for LM baseline
    public String template;

    public static void calculateStats(String inputFile, String annotationFile) throws Exception {
        // load input (querying output)
        QKBCResult r = Gson.fromJson(FileUtils.getContent(inputFile, "UTF-8"), QKBCResult.class);

        // load annotation

        HashMap<String, Boolean> kbcId2Eval = null;

        if (annotationFile != null) {
            kbcId2Eval = new HashMap<>();
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(new FileReader(annotationFile, StandardCharsets.UTF_8));
            for (CSVRecord o : records) {
                String id = o.get(0);
                String eval = o.get(8);
                if (eval.equals("TRUE")) {
                    kbcId2Eval.put(id, true);
                } else if (eval.equals("FALSE")) {
                    kbcId2Eval.put(id, false);
                } else {
                    throw new RuntimeException("invalid eval: " + annotationFile + " @ " + id);
                }
            }
        }

        System.out.println(inputFile);
        System.out.println(r.ctxList.toString());
        System.out.println(String.format("%12s%12s%12s%12s%16s%16s%12s", "iter", "#facts", "prec.@cwa", "prec.", "rec.@cwa|x1e-2", "ext.", "#noise"));
        int it = 0;
        do {
            int currentIt = ++it;
            ArrayList<RelationInstance> currentEffectiveInstances = r.instances.stream()
                    .filter(o -> o.effectivePositiveIterIndices.contains(currentIt))
                    .collect(Collectors.toCollection(ArrayList::new));
            ArrayList<RelationInstance> currentNoise = r.instances.stream().filter(o -> o.noiseIterIndices.contains(currentIt))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (currentEffectiveInstances.size() == 0) {
                break;
            }

            // nFacts
            int nFacts = currentEffectiveInstances.size();

            // recall, ext (only when refinement by time == false)
            double recall = -1;
            double precCWA = -1;

            int ext;
            int nTrueInGroundTruth = 0;
            int nInGroundTruth = 0;

            if (r.groundTruthSize != null) {
                ext = 0;

                loop:
                for (RelationInstance ri : currentEffectiveInstances) {
                    if (ri.groundtruth == null) {
                        ext++;
                        continue;
                    } else {
                        ++nInGroundTruth;
                        if (ri.groundtruth) {
                            ++nTrueInGroundTruth;
                        }
                    }
                }
                recall = 1.0 * nTrueInGroundTruth / r.groundTruthSize * 100;
                precCWA = nInGroundTruth == 0 ? -1 : 1.0 * nTrueInGroundTruth / nInGroundTruth;
            } else {
                ext = currentEffectiveInstances.size();
            }

            // prec
            double prec = -1;
            if (kbcId2Eval != null) {
                int sampledTrue = 0, totalSampled = 0;
                for (RelationInstance ri : currentEffectiveInstances) {
                    if (ri.sampledEffectivePositiveIterIndices.contains(currentIt)) {
                        ++totalSampled;
                        if (kbcId2Eval.get(ri.kbcId)) {
                            ++sampledTrue;
                        }
                    }
                }

                // prec
                double precOutsideGroundTruth = totalSampled == 0 ? 0 : 1.0 * sampledTrue / totalSampled;
                prec = (precOutsideGroundTruth * ext + nTrueInGroundTruth) / currentEffectiveInstances.size();
            }

            System.out.println(String.format("%12d%12d%12.3f%12.3f%16.3f%16s%12d", it, nFacts, precCWA, prec, recall,
                    ext == -1 ? String.format("%.3f", 1.0 * ext) : String.format("%d(%.3f)", ext, 1.0 * ext / currentEffectiveInstances.size()),
                    currentNoise.size()
            ));

//            currentEffectiveInstances.stream().forEach(ri ->
//                    System.out.println(ri.entity + " -- " + r.predicate + (r.refinementByTime ? "@" + ri.getYearCtx() : "") + " -- "
//                            + Number.getWrittenString(ri.getQuantity().value, true) + "," + ri.getQuantity().getKgUnit().entity
//                            + " -- " + ri.getSentence() + " -- " + ri.getSource()));

//            currentNoise.stream().forEach(ri ->
//                    System.out.println(ri.entity + " -- " + r.predicate + (r.refinementByTime ? "@" + ri.getYearCtx() : "") + " -- "
//                            + Number.getWrittenString(ri.getQuantity().value, true) + "," + ri.getQuantity().getKgUnit().entity
//                            + " -- " + ri.getSentence() + " -- " + ri.getSource()));
        } while (true);
    }

    public static void main(String[] args) throws Exception {
        // non-parametric
        // bootstrapping
        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/building_height_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - building_height_our.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/mountain_elevation_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - mountain_elevation_our.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/river_length_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - river_length_our.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/stadium_capacity_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - stadium_capacity_our.csv");

        // original
        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/company_revenue_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - company_revenue_our.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/city_altitude_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - city_altitude_our.csv");

        // parametric
        // bootstrapping
        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/building_height_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - building_height_our.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/mountain_elevation_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - mountain_elevation_our.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/river_length_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - river_length_our.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/stadium_capacity_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - stadium_capacity_our.csv");

        // original
        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/company_revenue_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - company_revenue_our.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/city_altitude_ourP.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - city_altitude_our.csv");

        // QSEARCH
        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/building_height_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/mountain_elevation_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/river_length_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/stadium_capacity_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/company_revenue_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/city_altitude_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv");

        // LM
        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/building_height_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/mountain_elevation_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/river_length_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/stadium_capacity_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/city_altitude_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/company_revenue_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv");
    }
}
