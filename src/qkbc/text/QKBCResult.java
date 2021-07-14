package qkbc.text;

import eval.qkbc.WikidataGroundTruthExtractor;
import model.quantity.kg.KgUnit;
import util.FileUtils;
import util.Gson;
import util.Number;
import util.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class QKBCResult {
    public String predicate;
    public ArrayList<String> ctxList;
    public ArrayList<RelationInstance> instances;

    private static void markEffectiveFactsForIter(int iter, ArrayList<RelationInstance> instances, boolean refinementByTime) {
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


    public static void markEffectiveAndGroundTruthFacts(
            QKBCResult r,
            Map<String, WikidataGroundTruthExtractor.PredicateNumericalFact> groundTruth,
            boolean refinementByTime) {
        for (RelationInstance ri : r.instances) {
            ri.effectivePositiveIterIndices = new ArrayList<>();
        }

        // mark effective
        int it = 0;
        do {
            int currentIt = ++it;
            boolean goodIt = false;
            for (RelationInstance ri : r.instances) {
                if (ri.positiveIterIndices.contains(it)) {
                    goodIt = true;
                }
            }
            if (!goodIt) {
                break;
            }
            ArrayList<RelationInstance> currentInstances = r.instances.stream().filter(o -> o.positiveIterIndices.contains(currentIt))
                    .collect(Collectors.toCollection(ArrayList::new));

            markEffectiveFactsForIter(currentIt, currentInstances, refinementByTime);
        } while (true);

        // mark groundtruth (only when refinementByTime == false)
        if (groundTruth != null && !refinementByTime) {
            for (RelationInstance ri : r.instances) {
                if (ri.effectivePositiveIterIndices.size() == 0) {
                    continue;
                }
                WikidataGroundTruthExtractor.PredicateNumericalFact f = groundTruth.get(ri.entity);
                if (f == null) {
                    continue;
                }
                for (Pair<Double, String> p : f.quantities) {
                    double v = p.first * KgUnit.getKgUnitFromEntityName(p.second).conversionToSI;
                    if (Number.relativeNumericDistance(v, ri.quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                        if (ri.eval == null) {
                            ri.eval = true;
                        } else {
                            if (!ri.eval) {
                                throw new RuntimeException("inconsistent eval vs. groundtruth for " + ri.entity + " -- " + r.predicate);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }


    public static void calculateStats(String inputFile, String groundTruthFile, boolean refinementByTime) {
        // load input (querying output)
        QKBCResult r = Gson.fromJson(FileUtils.getContent(inputFile, "UTF-8"), QKBCResult.class);

        // load groundTruth
        Map<String, WikidataGroundTruthExtractor.PredicateNumericalFact> groundTruth = null;
        if (groundTruthFile != null) {
            groundTruth = new HashMap<>();
            for (String line : FileUtils.getLineStream(groundTruthFile)) {
                WikidataGroundTruthExtractor.PredicateNumericalFact f = Gson.fromJson(line, WikidataGroundTruthExtractor.PredicateNumericalFact.class);
                groundTruth.put(f.e, f);
            }
        }

        markEffectiveAndGroundTruthFacts(r, groundTruth, refinementByTime);

        System.out.println(inputFile);
        System.out.println(r.ctxList.toString());
        System.out.println(String.format("%12s%12s%12s%16s%16s", "iter", "#facts", "prec.", "recall(x1e-2)", "ext."));
        int it = 0;
        do {
            int currentIt = ++it;
            ArrayList<RelationInstance> currentEffectiveInstances = r.instances.stream()
                    .filter(o -> o.effectivePositiveIterIndices.contains(currentIt))
                    .collect(Collectors.toCollection(ArrayList::new));
//            ArrayList<RelationInstance> currentNoise = r.instances.stream().filter(o -> o.noiseIterIndices.contains(currentIt))
//                    .collect(Collectors.toCollection(ArrayList::new));
            if (currentEffectiveInstances.size() == 0) {
                break;
            }

            // nFacts
            int nFacts = currentEffectiveInstances.size();

            // recall, ext (only when refinement by time == false)
            double recall = -1;
            int ext = -1;
            if (groundTruth != null && !refinementByTime) {
                int nTrue = 0;
                ext = 0;
                for (RelationInstance ri : currentEffectiveInstances) {
                    WikidataGroundTruthExtractor.PredicateNumericalFact f = groundTruth.get(ri.entity);
                    if (f == null) {
                        ext++;
                        continue;
                    }
                    for (Pair<Double, String> p : f.quantities) {
                        double v = p.first * KgUnit.getKgUnitFromEntityName(p.second).conversionToSI;
                        if (Number.relativeNumericDistance(v, ri.quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                            if (ri.eval != null && !ri.eval) {
                                throw new RuntimeException("inconsistent eval vs. groundtruth for " + ri.entity + " -- " + r.predicate);
                            }
                            nTrue++;
                            break;
                        }
                    }

                }
                recall = 1.0 * nTrue / groundTruth.size() * 100;
            }

            // TODO: prec
            double prec = -1;

            System.out.println(String.format("%12d%12d%12.3f%16.3f%16s", it, nFacts, prec, recall,
                    ext == -1 ? String.format("%.3f", 1.0 * ext) : String.format("%d(%.3f)", ext, 1.0 * ext / currentEffectiveInstances.size())));

//            currentEffectiveInstances.stream().forEach(ri ->
//                    System.out.println(ri.entity + " -- " + r.predicate + (refinementByTime ? "@" + ri.getYearCtx() : "") + " -- "
//                            + Number.getWrittenString(ri.getQuantity().value, true) + "," + ri.getQuantity().getKgUnit().entity
//                            + " -- " + ri.getSentence() + " -- " + ri.getSource()));
        } while (true);
    }

    public static void main(String[] args) {
        // bootstrapping
        calculateStats("eval/qkbc/exp_1/qsearch_queries/building_height_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height", false);

        calculateStats("eval/qkbc/exp_1/qsearch_queries/mountain_elevation_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation", false);

        calculateStats("eval/qkbc/exp_1/qsearch_queries/river_length_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length", false);

        calculateStats("eval/qkbc/exp_1/qsearch_queries/stadium_capacity_ourN.json",
                "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity", false);

        // original
        calculateStats("eval/qkbc/exp_1/qsearch_queries/company_revenue_ourN.json",
                null, true);

        calculateStats("eval/qkbc/exp_1/qsearch_queries/city_altitude_ourN.json",
                null, false);
    }
}
