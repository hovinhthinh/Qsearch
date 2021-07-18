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

    public static void calculateStats(String inputFile, String annotationFile) throws Exception {
        // load input (querying output)
        QKBCResult r = Gson.fromJson(FileUtils.getContent(inputFile, "UTF-8"), QKBCResult.class);

        // load annotation

        Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(new FileReader(annotationFile, StandardCharsets.UTF_8));

        HashMap<String, Boolean> kbcId2Eval = new HashMap<>();
        records.forEach(o -> {
            String id = o.get(0);
            String eval = o.get(8);
            if (eval.equals("TRUE")) {
                kbcId2Eval.put(id, true);
            } else if (eval.equals("FALSE")) {
                kbcId2Eval.put(id, false);
            } else {
                throw new RuntimeException("invalid eval: " + annotationFile + " @ " + id);
            }
        });


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

            int ext = -1;
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
            }

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
            double prec = (precOutsideGroundTruth * ext + nTrueInGroundTruth) / currentEffectiveInstances.size();

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
        // bootstrapping
        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/building_height_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - building_height_our.csv");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/our_output_fact/mountain_elevation_ourN.json",
                "eval/qkbc/exp_1/qsearch_queries/annotation/qkbc eval - mountain_elevation_our.csv");

//        calculateStats("eval/qkbc/exp_1/qsearch_queries/river_length_ourN.json");
//
//        calculateStats("eval/qkbc/exp_1/qsearch_queries/stadium_capacity_ourN.json");
//
//        // original
//        calculateStats("eval/qkbc/exp_1/qsearch_queries/company_revenue_ourN.json");
//
//        calculateStats("eval/qkbc/exp_1/qsearch_queries/city_altitude_ourN.json");
    }
}
