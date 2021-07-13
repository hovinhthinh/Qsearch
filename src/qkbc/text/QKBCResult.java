package qkbc.text;

import eval.qkbc.WikidataGroundTruthExtractor;
import util.FileUtils;
import util.Gson;
import util.Number;
import util.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class QKBCResult {
    public ArrayList<String> ctxList;
    public ArrayList<RelationInstance> instances;

    public static void markEffectiveFacts(int iter, ArrayList<RelationInstance> instances, boolean refinementByTime) {
        for (RelationInstance ri : instances) {
            ri.effectivePositiveIterIndices = new ArrayList<>();
        }
        if (refinementByTime) {
            ArrayList<RelationInstance> withYears = instances.stream().filter(ri -> ri.getYearCtx() != null)
                    .collect(Collectors.toCollection(ArrayList::new));
            ArrayList<RelationInstance> withoutYears = instances.stream().filter(ri -> ri.getYearCtx() == null)
                    .collect(Collectors.toCollection(ArrayList::new));
            // entity+time to values + freqs
            HashMap<String, ArrayList<Pair<RelationInstance, Integer>>> map = new HashMap<>();

            loop:
            for (RelationInstance ri : withYears) {
                String key = ri.entity + ":" + ri.getYearCtx();
                if (!map.containsKey(key)) {
                    map.put(key, new ArrayList<>());
                }


                List<Pair<RelationInstance, Integer>> values = map.get(key);
                for (Pair<RelationInstance, Integer> p : values) {
                    if (Number.relativeNumericDistance(ri.quantityStdValue, p.first.quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                        p.second++;
                        continue loop;
                    }
                }

                values.add(new Pair<>(ri, 1));
            }

            for (RelationInstance ri : withoutYears) {
                for (Map.Entry<String, ArrayList<Pair<RelationInstance, Integer>>> e : map.entrySet()) {
                    if (!e.getKey().startsWith(ri.entity + ":")) {
                        continue;
                    }
                    for (Pair<RelationInstance, Integer> p : e.getValue()) {
                        if (Number.relativeNumericDistance(ri.quantityStdValue, p.first.quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                            p.second++;
                            break;
                        }
                    }
                }
            }

            for (ArrayList<Pair<RelationInstance, Integer>> arr : map.values()) {
                Collections.sort(arr, (a, b) -> b.second.compareTo(a.second));
                arr.get(0).first.effectivePositiveIterIndices.add(iter);
            }
        } else {
            // entity+time to values + freqs
            HashMap<String, ArrayList<Pair<RelationInstance, Integer>>> map = new HashMap<>();

            loop:
            for (RelationInstance ri : instances) {
                if (!map.containsKey(ri.entity)) {
                    map.put(ri.entity, new ArrayList<>());
                }
                List<Pair<RelationInstance, Integer>> values = map.get(ri.entity);
                for (Pair<RelationInstance, Integer> p : values) {
                    if (Number.relativeNumericDistance(ri.quantityStdValue, p.first.quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                        p.second++;
                        continue loop;
                    }
                }

                values.add(new Pair<>(ri, 1));
            }

            for (ArrayList<Pair<RelationInstance, Integer>> arr : map.values()) {
                Collections.sort(arr, (a, b) -> b.second.compareTo(a.second));
                arr.get(0).first.effectivePositiveIterIndices.add(iter);
            }
        }
    }

    public static void printStats(String inputFile, String groundTruthFile, boolean refinementByTime) {
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

        System.out.println(inputFile);
        System.out.println(r.ctxList.toString());
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

            int currentIt = it;


            ArrayList<RelationInstance> currentInstances = r.instances.stream().filter(o -> o.positiveIterIndices.contains(currentIt))
                    .collect(Collectors.toCollection(ArrayList::new));
//            ArrayList<RelationInstance> currentNoise = r.instances.stream().filter(o -> o.noiseIterIndices.contains(currentIt))
//                    .collect(Collectors.toCollection(ArrayList::new));

            markEffectiveFacts(it, currentInstances, refinementByTime);
            nFacts = (int) currentInstances.stream().filter(o -> o.effectivePositiveIterIndices.size() > 0).count();

            System.out.println(String.format("%12d%12d%12.3f%12.3f%12d", it, nFacts, prec, recall, ext));

            // TODO

//            currentInstances.stream().filter(o -> o.effectivePositiveIterIndices.size() > 0).forEach(ri ->
//                    System.out.println(ri.entity + " -- revenue" + (refinementByTime ? " " + ri.getYearCtx() : "") + " -- "
//                            + ri.getQuantity().value + "," + ri.getQuantity().getKgUnit().entity
//                            + " -- " + ri.getSentence() + " -- " + ri.getSource()));

        } while (true);
    }

    public static void main(String[] args) {
        // bootstrapping
        printStats("eval/qkbc/exp_1/qsearch_queries/building_height_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height", false);

        printStats("eval/qkbc/exp_1/qsearch_queries/mountain_elevation_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation", false);

        printStats("eval/qkbc/exp_1/qsearch_queries/river_length_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length", false);

        printStats("eval/qkbc/exp_1/qsearch_queries/stadium_capacity_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity", false);

        // original
        printStats("eval/qkbc/exp_1/qsearch_queries/company_revenue_ourN.json",
                null, true);

        printStats("eval/qkbc/exp_1/qsearch_queries/city_altitude_ourN.json",
                null, false);
    }
}
