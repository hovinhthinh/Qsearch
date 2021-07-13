package qkbc.text;

import eval.qkbc.WikidataGroundTruthExtractor;
import util.FileUtils;
import util.Gson;
import util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class QKBCResult {
    public ArrayList<String> ctxList;
    public ArrayList<RelationInstance> instances;

    public static void markEffectiveFacts(ArrayList<RelationInstance> instances, boolean includeTime) {
        for (RelationInstance ri : instances) {
            ri.effective = false;
        }
        if (includeTime) {
            ArrayList<RelationInstance> withYears = instances.stream().filter(ri -> ri.getYearCtx() != null)
                    .collect(Collectors.toCollection(ArrayList::new));
            ArrayList<RelationInstance> withoutYears = instances.stream().filter(ri -> ri.getYearCtx() == null)
                    .collect(Collectors.toCollection(ArrayList::new));
            // entity+time to values + freqs
            HashMap<String, List<Pair<Double, Integer>>> map = new HashMap<>();

            for (RelationInstance ri : withYears) {
                // TODO
            }

        } else {

        }
    }

    public static void printStats(String inputFile, String groundTruthFile) {
        // load groundTruth
        ArrayList<WikidataGroundTruthExtractor.PredicateNumericalFact> groundTruth = null;
        if (groundTruthFile != null) {
            groundTruth = new ArrayList<>();
            for (String line : FileUtils.getLineStream(groundTruthFile)) {
                groundTruth.add(Gson.fromJson(line, WikidataGroundTruthExtractor.PredicateNumericalFact.class));
            }
        }

        // load input (querying output)
        QKBCResult r = Gson.fromJson(FileUtils.getContent(inputFile, "UTF-8"), QKBCResult.class);

        System.out.println(String.format("%12s%12s%12s%12s%12s", "iter", "#facts", "prec.", "recall", "ext."));
        int it = 0;
        do {
            ++it;
            boolean goodIt = false;
            for (RelationInstance ri : r.instances) {
                if (ri.positiveIterIndices.contains(it)) {
                    goodIt = true;
                }
            }
            if (!goodIt) {
                break;
            }

            int nFacts = 0;
            double prec = -1;
            double recall = -1;
            int ext = -1;
            // to compute

            System.out.println(String.format("%12d%12d%12.3f%12.3f%12d", it, nFacts, prec, recall, ext));
        } while(true);
    }

    public static void main(String[] args) {
        printStats("eval/qkbc/exp_1/qsearch_queries/building_height_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height");
    }
}
