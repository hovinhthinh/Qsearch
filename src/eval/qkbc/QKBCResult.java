package eval.qkbc;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import qkbc.text.RelationInstance;
import storage.text.migrate.ChronicleMapQfactStorage;
import storage.text.migrate.TypeMatcher;
import util.FileUtils;
import util.Gson;

import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        calculateStats(inputFile, annotationFile, null, null);
    }

    public static void calculateStats(String inputFile, String annotationFile,
                                      String groundTruthFile, String type) throws Exception {
        Integer nGroundtruth = null;
        if (groundTruthFile != null) {
            TypeMatcher matcher = new TypeMatcher(type);
            HashSet<String> eS = new HashSet<>();
            for (String e : ChronicleMapQfactStorage.SEARCHABLE_ENTITIES) {
                if (matcher.match(e)) {
                    eS.add(e);
                }
            }
            nGroundtruth = (int) WikidataGroundTruthExtractor.loadPredicateGroundTruthFromFile(groundTruthFile).stream()
                    .filter(o -> eS.contains(o.e))
                    .mapToInt(o -> o.nFacts()).sum();
        }

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
//                recall = 1.0 * nTrueInGroundTruth / r.groundTruthSize * 100;
                recall = 1.0 * nTrueInGroundTruth / (nGroundtruth != null ? nGroundtruth : r.groundTruthSize) * 100;
                precCWA = nInGroundTruth == 0 ? -1 : 1.0 * nTrueInGroundTruth / nInGroundTruth;
            } else {
                ext = currentEffectiveInstances.size();
            }

            // prec
            double precOutsideGroundTruth = -1, prec = -1;
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
                precOutsideGroundTruth = totalSampled == 0 ? 0 : 1.0 * sampledTrue / totalSampled;
                prec = (precOutsideGroundTruth * ext + nTrueInGroundTruth) / currentEffectiveInstances.size();
            }

            System.out.println(String.format("%12d%12d%12.3f%12.3f%16.3f%16s%12d", it, nFacts, precCWA, prec, recall,
                    ext == -1 ? String.format("%.3f", 1.0 * ext) : String.format("%.3f", 1.0 * ext * precOutsideGroundTruth / currentEffectiveInstances.size()),
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
        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/building_height_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - building_height_our.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height", "<wordnet_building_102913152>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/mountain_elevation_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - mountain_elevation_our.csv",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation", "<http://schema.org/Mountain>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/river_length_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - river_length_our.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length", "<wordnet_river_109411430>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/stadium_capacity_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - stadium_capacity_our.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity", "<wordnet_stadium_104295881>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/company_revenue_ourN_gt.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - company_revenue_our_gt.csv",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-company_revenue", "<wordnet_company_108058098>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/powerstation_capacity_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - powerstation_capacity_our.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-powerStation_capacity", "<wordnet_power_station_103996655>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/earthquake_magnitude_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - earthquake_magnitude_our.csv",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-earthquake_magnitude", "<wordnet_earthquake_107428954>");

        // QSEARCH
        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/building_height_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height", "<wordnet_building_102913152>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/mountain_elevation_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation", "<http://schema.org/Mountain>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/river_length_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length", "<wordnet_river_109411430>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/stadium_capacity_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - qsearch_annotation.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity", "<wordnet_stadium_104295881>");


        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/company_revenue_qs_gt.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - company_revenue_qs_gt.csv",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-company_revenue", "<wordnet_company_108058098>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/powerStation_capacity_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - powerStation_capacity_and_earthquake_magnitude_qs.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-powerStation_capacity", "<wordnet_power_station_103996655>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/qs_output_fact/earthquake_magnitude_qs.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_qs/qkbc eval - powerStation_capacity_and_earthquake_magnitude_qs.csv",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-earthquake_magnitude", "<wordnet_earthquake_107428954>");

        // LM
        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/building_height_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height", "<wordnet_building_102913152>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/mountain_elevation_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation", "<http://schema.org/Mountain>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/river_length_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length", "<wordnet_river_109411430>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/stadium_capacity_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - lm_annotation.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity", "<wordnet_stadium_104295881>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/company_revenue_lm_gt.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - company_revenue_lm_gt.csv",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-company_revenue", "<wordnet_company_108058098>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/powerStation_capacity_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - powerStation_capacity_and_earthquake_magnitude_lm.csv",
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-powerStation_capacity", "<wordnet_power_station_103996655>");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/lm_output_fact/earthquake_magnitude_lm.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation_lm/qkbc eval - powerStation_capacity_and_earthquake_magnitude_lm.csv",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-earthquake_magnitude", "<wordnet_earthquake_107428954>");
    }
}
