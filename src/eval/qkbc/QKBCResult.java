package eval.qkbc;

import qkbc.text.RelationInstance;
import util.FileUtils;
import util.Gson;
import util.Number;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class QKBCResult {
    public static final int ANNOTATION_SAMPLING_SIZE = 30;

    public String predicate;
    public boolean refinementByTime;
    public Integer groundTruthSize;
    public ArrayList<String> ctxList;
    public int nIterations;
    public ArrayList<RelationInstance> instances;

    public static void calculateStats(String inputFile) {
        // load input (querying output)
        QKBCResult r = Gson.fromJson(FileUtils.getContent(inputFile, "UTF-8"), QKBCResult.class);

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
            if (r.groundTruthSize != null) {
                int nTrue = 0;
                int nInGroundTruth = 0;

                ext = 0;
                loop:
                for (RelationInstance ri : currentEffectiveInstances) {
                    if (ri.groundtruth == null) {
                        ext++;
                        continue;
                    } else {
                        ++nInGroundTruth;
                        if (ri.groundtruth) {
                            ++nTrue;
                        }
                    }
                }
                recall = 1.0 * nTrue / r.groundTruthSize * 100;
                precCWA = nInGroundTruth == 0 ? -1 : 1.0 * nTrue / nInGroundTruth;
            }

            // TODO: prec
            double prec = -1;

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

    public static void main(String[] args) {
        // bootstrapping
        calculateStats("eval/qkbc/exp_1/qsearch_queries/building_height_ourN.json");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/mountain_elevation_ourN.json");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/river_length_ourN.json");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/stadium_capacity_ourN.json");

        // original
        calculateStats("eval/qkbc/exp_1/qsearch_queries/company_revenue_ourN.json");

        calculateStats("eval/qkbc/exp_1/qsearch_queries/city_altitude_ourN.json");
    }
}
