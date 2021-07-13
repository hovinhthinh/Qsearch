package qkbc.text;

import eval.qkbc.WikidataGroundTruthExtractor;
import model.quantity.kg.KgUnit;
import nlp.NLP;
import qkbc.distribution.DistributionFitter;
import qkbc.distribution.IntegralDistributionApproximator;
import server.text.ResultInstance;
import server.text.handler.search.SearchResult;
import shaded.org.apache.http.client.utils.URIBuilder;
import uk.ac.susx.informatics.Morpha;
import umontreal.ssj.probdist.ContinuousDistribution;
import util.Number;
import util.*;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ContextStats {
    private HashMap<String, List<Pair<Double, Double>>> entity2ValuesAndQueryingConf = new HashMap<>();
    private HashMap<String, List<Double>> entity2ValuesDuplicated = new HashMap<>();

    public String context;

    public ContextStats(String context) {
        this.context = context;
    }

    public void addDuplicatedInstance(String entity, double quantityStdValue) {
        if (!entity2ValuesDuplicated.containsKey(entity)) {
            entity2ValuesDuplicated.put(entity, new ArrayList<>());
        }
        for (Double v : entity2ValuesDuplicated.get(entity)) {
            if (Number.relativeNumericDistance(v, quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                return;
            }
        }
        entity2ValuesDuplicated.get(entity).add(quantityStdValue);
    }

    public void addInstance(String entity, double quantityStdValue, double conf) {
        if (!entity2ValuesAndQueryingConf.containsKey(entity)) {
            entity2ValuesAndQueryingConf.put(entity, new ArrayList<>());
        }
        for (Pair<Double, Double> v : entity2ValuesAndQueryingConf.get(entity)) {
            if (Number.relativeNumericDistance(v.first, quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                if (v.second < conf) {
                    v.second = conf;
                }
                return;
            }
        }
        entity2ValuesAndQueryingConf.get(entity).add(new Pair<>(quantityStdValue, conf));
    }

    public int support() {
        return entity2ValuesDuplicated.entrySet().stream().mapToInt(o -> o.getValue().size()).sum();
    }

    public double relativeSupport(int denominator) {
        return ((double) support()) / denominator;
    }

    public double extensibility() {
        int sup = support();
        int total = entity2ValuesAndQueryingConf.entrySet().stream().mapToInt(o -> o.getValue().size()).sum();
        return 1 - ((double) sup) / total;
    }

    public double queryingConfidence() {
        return entity2ValuesAndQueryingConf.entrySet().stream().flatMap(o -> o.getValue().stream())
                .mapToDouble(o -> o.second).average().getAsDouble();
    }

    public double distConfidence(IntegralDistributionApproximator positiveDistAppr) {
        return entity2ValuesAndQueryingConf.entrySet().stream().flatMap(o -> o.getValue().stream())
                .mapToDouble(o -> positiveDistAppr.getEstimatedPValue(o.first)).average().getAsDouble();
    }

    private static double RELATIVE_SUPPORT_WEIGHT = 0.1;
    private static double QUERYING_CONF_WEIGHT = 0.5; // for company-revenue, use 0.2 instead of 0.5
    private static double DIST_CONF_WEIGHT = 0.2; // for company-revenue, use 0 instead of 0.2

    public double totalConfidence(int relSupportDenom, IntegralDistributionApproximator positiveDistAppr) {
        return (relativeSupport(relSupportDenom) * RELATIVE_SUPPORT_WEIGHT
                + queryingConfidence() * QUERYING_CONF_WEIGHT
                + distConfidence(positiveDistAppr) * DIST_CONF_WEIGHT)
                * Math.pow(1.01, context.split(" ").length);
    }
}

public class QKBCRunner {
    public static final String QSEARCH_END_POINT = "http://sedna:6993/kbc_text";

    /*
    static class EntityFact {
        String quantity;
        Double qtStandardValue;
        ArrayList<String> sources = new ArrayList<>();

        public EntityFact(String quantity, Double qtStandardValue, String source) {
            this.quantity = quantity;
            this.qtStandardValue = qtStandardValue;
            this.sources.add(source);
        }
    }
    */

    private static Random RANDOM = new Random(120993);

    private static ArrayList<RelationInstance> query(String yagoType, String context, KgUnit quantitySiUnit) {
        try {
            URIBuilder b = new URIBuilder(QSEARCH_END_POINT);
            b.addParameter("type", yagoType);
            b.addParameter("context", context);
            b.addParameter("quantitySiUnit", quantitySiUnit.entity);
            b.addParameter("ntop", "1000000");
            b.addParameter("n-evidence", "100");
//            b.addParameter("min-entity-conf", "1");
            b.addParameter("min-qfact-conf", "0.5");
            SearchResult sr = Gson.fromJson(Crawler.getContentFromUrl(b.toString()), SearchResult.class);

            ArrayList<RelationInstance> result = new ArrayList<>();

            for (ResultInstance ri : sr.topResults) {
                for (ResultInstance.SubInstance si : ri.subInstances) {
                    result.add(new RelationInstance(ri.entity, si.quantity, si.quantityStandardValue, 1 / si.score, si.kbcId));
                }
            }

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static void printToFile(LinkedHashSet<String> ctxList, List<RelationInstance> instances, String outputFile) {
        QKBCResult r = new QKBCResult();
        r.ctxList = new ArrayList<>(ctxList);
        r.instances = new ArrayList<>(instances);

        PrintWriter out = FileUtils.getPrintWriter(outputFile, "UTF-8");
        out.println(Gson.toJson(r));
        /*
        HashMap<String, List<EntityFact>> e2f = new HashMap<>();
        loop:
        for (RelationInstance i : instances) {
            e2f.putIfAbsent(i.entity, new ArrayList<>());
            List<EntityFact> fs = e2f.get(i.entity);

            String iSource = i.getSource();
            for (EntityFact f : fs) {
                if (Number.relativeNumericDistance(f.qtStandardValue, i.quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                    if (!f.sources.contains(iSource)) {
                        f.sources.add(iSource);
                    }
                    continue loop;
                }
            }

            fs.add(new EntityFact(i.quantity, i.quantityStdValue, iSource));
        }

        e2f.entrySet().stream().forEach(e -> {
            for (EntityFact f : e.getValue()) {
                out.println(String.format("%s\t%s\t%s", e.getKey(), f.quantity, String.join("\t", f.sources)));
            }
        });
        */

        out.close();
    }

    public static boolean contextIncluding(String superset, String subset) {
        Set<String> sup = NLP.splitSentence(superset).stream().map(x -> NLP.fastStemming(x.toLowerCase(), Morpha.any))
                .collect(Collectors.toCollection(HashSet::new));

        for (String t : NLP.splitSentence(subset).stream()
                .map(x -> NLP.fastStemming(x.toLowerCase(), Morpha.any))
                .collect(Collectors.toList())) {
            if (!sup.contains(t)) {
                return false;
            }
        }
        return true;
    }

    public static void harvest(String type, String seedCtx, KgUnit quantitySiUnit,
                               boolean useParametricDenoising,
                               double groupConfidenceThreshold, int maxNIter,
                               String groundTruthFile, int maxGroundTruthSize,
                               String outputFile) {
        Map<String, WikidataGroundTruthExtractor.PredicateNumericalFact> grouthtruth = groundTruthFile == null ? null :
                WikidataGroundTruthExtractor.loadPredicateGroundTruthFromFile(groundTruthFile).stream()
                        .collect(Collectors.toMap(f -> f.e, f -> f));


        HashMap<String, RelationInstance> kbcId2RelationInstanceMap = new HashMap<>();

        ArrayList<RelationInstance> riList = new ArrayList<>();
        List<RelationInstance> groundTruthList = new ArrayList<>(), groundTruthListSampled = new ArrayList<>();
        if (grouthtruth != null) {
            for (WikidataGroundTruthExtractor.PredicateNumericalFact f : grouthtruth.values()) {
                for (Pair<Double, String> p : f.quantities) {
                    groundTruthList.add(RelationInstance.newRelationInstanceFromGroundTruth(
                            f.e, p.first * KgUnit.getKgUnitFromEntityName(p.second).conversionToSI
                    ));
                    // only allow 1 quantity for each entity from the groundtruth
                    break;
                }
            }
            groundTruthListSampled = groundTruthList;
            if (maxGroundTruthSize >= 0 && groundTruthList.size() > maxGroundTruthSize) {
                synchronized (RANDOM) {
                    Collections.shuffle(groundTruthList, RANDOM);
                }
                groundTruthListSampled = groundTruthList.subList(0, maxGroundTruthSize);
            }
        }


        LinkedHashSet<String> ctxList = new LinkedHashSet<>();

        Queue<String> ctxQueue = new LinkedList<>() {{
            if (seedCtx != null) {
                add(seedCtx);
            } else {
                add("");
            }
        }};

        int iter = 0;
        if (seedCtx == null) {
            --iter;
        }

        if (maxNIter == -1) {
            maxNIter = Integer.MAX_VALUE;
        }

        do {
            System.out.println(String.format("======== Iteration #%d ========", ++iter));
            ctxList.addAll(ctxQueue);
            System.out.println(String.format("Context list: %s", ctxList.toString()));
            while (!ctxQueue.isEmpty()) {
                String frontCtx = ctxQueue.remove();
                for (RelationInstance i : query(type, frontCtx, quantitySiUnit)) {
                    if (!kbcId2RelationInstanceMap.containsKey(i.kbcId)) {
                        kbcId2RelationInstanceMap.put(i.kbcId, i);
                        riList.add(i);
                    }
                    RelationInstance storedI = kbcId2RelationInstanceMap.get(i.kbcId);
                    // iter == 0 means that we ignore querying step. However we still query to get the list of answers,
                    // but set their conf to 0.
                    storedI.score = iter == 0 ? 0 : Math.max(storedI.score, i.score);
                }
            }
            // query reformulation mining
            ArrayList<RelationInstance> mostlyPositive = riList.stream()
                    .filter(i -> i.score >= groupConfidenceThreshold)
                    .collect(Collectors.toCollection(ArrayList::new));

            ArrayList<RelationInstance> mostlyPositiveWithGroundTruthSampled = new ArrayList<>(mostlyPositive);
            mostlyPositiveWithGroundTruthSampled.addAll(groundTruthListSampled);

            Pair<ContinuousDistribution, Double> positiveDist = useParametricDenoising
                    ? RelationInstanceNoiseFilter.consistencyBasedParametricDistributionNoiseFilter(mostlyPositiveWithGroundTruthSampled)
                    : RelationInstanceNoiseFilter.consistencyBasedNonParametricDistributionNoiseFilter(mostlyPositiveWithGroundTruthSampled);


            // print stats
            System.out.println(String.format("Mostly-positive size: %d", mostlyPositive.size()));
            System.out.println(String.format("Noise: %d", mostlyPositive.stream().filter(i -> !i.positive).count()));
            for (RelationInstance i : mostlyPositive) {
                if (!i.positive) {
                    i.noiseIterIndices.add(iter);
                    System.out.println(String.format("  %s  |  %s  |  %s  |  %s",
                            i.entity, i.quantity, i.getSentence(), i.getSource()));
                } else {
                    i.positiveIterIndices.add(iter);
                }
            }
            List<RelationInstance> positivePart = mostlyPositive.stream().filter(i -> i.positive).collect(Collectors.toList());

            System.out.println(String.format("Positive samples: size: %d/%d (%d entities)",
                    positivePart.size(), riList.size(), positivePart.stream().collect(Collectors.groupingBy(o -> o.entity)).size()));

            System.out.println(String.format("Positive distribution: %s | mean: %.3f | p-value: %.3f",
                    positiveDist.first.toString(), positiveDist.first.getMean(), positiveDist.second));

            ArrayList<RelationInstance> positiveWithGroundTruth =
                    Stream.concat(mostlyPositive.stream().filter(i -> i.positive), groundTruthList.stream())
                            .collect(Collectors.toCollection(ArrayList::new));

            // mine more context in the unknown part
            Map<String, List<RelationInstance>> entity2PositiveInstances = positiveWithGroundTruth.stream()
                    .collect(Collectors.groupingBy(i -> i.entity));

            // also include the removed noise into the unknown part, as the noise detection is not perfect, especially at the beginning
            ArrayList<RelationInstance> unknownInstances = riList.stream().filter(i -> !i.positive).collect(Collectors.toCollection(ArrayList::new));

            HashMap<String, ContextStats> contextStats = new HashMap<>();

            for (RelationInstance u : unknownInstances) {
                ArrayList<String> ctx = u.getNormalContext();
                // stemming & unique
                ctx = new ArrayList<>(ctx.stream().map(x -> NLP.fastStemming(x.toLowerCase(), Morpha.any))
                        .collect(Collectors.toCollection(HashSet::new)));
                // uni-gram
                loop:
                for (String x : ctx) {
                    if (!contextStats.containsKey(x)) {
                        contextStats.put(x, new ContextStats(x));
                    }
                    ContextStats stats = contextStats.get(x);
                    // add unknown
                    stats.addInstance(u.entity, u.quantityStdValue, u.score);
                    // add duplicated unknown
                    List<RelationInstance> posList = entity2PositiveInstances.get(u.entity);
                    if (posList == null) {
                        continue;
                    }
                    for (RelationInstance p : posList) {
                        if (Number.relativeNumericDistance(u.quantityStdValue, p.quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                            stats.addDuplicatedInstance(u.entity, u.quantityStdValue);
                            continue;
                        }
                    }
                }
                // bi-gram
                for (int i = 0; i < ctx.size(); ++i) {
                    loop:
                    for (int j = i + 1; j < ctx.size(); ++j) {
                        String c0 = ctx.get(i), c1 = ctx.get(j);
                        String x = c0.compareTo(c1) < 0 ? c0 + " " + c1 : c1 + " " + c0;
                        if (!contextStats.containsKey(x)) {
                            contextStats.put(x, new ContextStats(x));
                        }
                        ContextStats stats = contextStats.get(x);

                        // add unknown
                        stats.addInstance(u.entity, u.quantityStdValue, u.score);
                        // add duplicated unknown
                        List<RelationInstance> posList = entity2PositiveInstances.get(u.entity);
                        if (posList == null) {
                            continue;
                        }
                        for (RelationInstance p : posList) {
                            if (Number.relativeNumericDistance(u.quantityStdValue, p.quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                                stats.addDuplicatedInstance(u.entity, u.quantityStdValue);
                                continue loop;
                            }
                        }
                    }
                }
            }


            IntegralDistributionApproximator positiveDistAppr = new IntegralDistributionApproximator(positiveDist.first);
            // Sort stats and output
            int currentIter = iter;
            int relSuppDenom = contextStats.entrySet().stream().mapToInt(e -> e.getValue().support()).max().orElse(0);
            List<ContextStats> sortedContextStats = contextStats.entrySet().stream().map(e -> e.getValue())
                    .filter(o -> {
                        for (String ctx : ctxList) {
                            if (contextIncluding(ctx, o.context)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .filter(o -> o.support() >= (groundTruthFile == null ? 5 : 10)) // 10 for bootstrapping, 5 for original settings
//                    .filter(o -> o.distConfidence(positiveDistAppr) >= 0.3)
                    .filter(o -> currentIter == 0 || o.queryingConfidence() >= 0.675)
                    .sorted((a, b) -> Double.compare(b.totalConfidence(relSuppDenom, positiveDistAppr), a.totalConfidence(relSuppDenom, positiveDistAppr)))
                    .collect(Collectors.toList());

            System.out.println("Mined context: " + sortedContextStats.size());
            int nPrinted = 0;
            for (ContextStats stats : sortedContextStats) {
                System.out.println(String.format("  ContextStats{'%s', support=%d, extensibility=%.3f, distConfidence=%.3f, queryingConfidence=%.3f, totalConf=%.3f}",
                        stats.context, stats.support(), stats.extensibility(), stats.distConfidence(positiveDistAppr), stats.queryingConfidence(), stats.totalConfidence(relSuppDenom, positiveDistAppr)));
                ++nPrinted;
                if (nPrinted == 10) {
                    break;
                }
            }

//            DistributionFitter.drawDistributionVsSamples(String.format("Iteration #%d", iter), positiveDist.first,
//                    mostlyPositiveWithGroundTruthSampled.stream().filter(i -> i.positive || i.isFromGroundTruth())
//                            .mapToDouble(i -> i.quantityStdValue).toArray(), false, true);

            // reformulate
            for (ContextStats stats : sortedContextStats) {
                ctxQueue.add(stats.context);
                break;
            }

            if (outputFile != null && (ctxQueue.isEmpty() || iter == maxNIter)) {
                printToFile(ctxList, mostlyPositive, outputFile);
            }
        } while (!ctxQueue.isEmpty() && iter < maxNIter);
    }

    public static void main(String[] args) {
        // Bootstrapping - parametric
        harvest("building", null, KgUnit.getKgUnitFromEntityName("<Metre>"),
                true, 0.9, 10,
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height", 200,
                "./eval/qkbc/exp_1/qsearch_queries/building_height_ourP.json");

        harvest("mountain", null, KgUnit.getKgUnitFromEntityName("<Metre>"),
                true, 0.9, 10,
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation", 200,
                "./eval/qkbc/exp_1/qsearch_queries/mountain_elevation_ourP.json");

//        harvest("river", null, KgUnit.getKgUnitFromEntityName("<Metre>"),
//                true, 0.9, 10,
//                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length", 200,
//                "./eval/qkbc/exp_1/qsearch_queries/river_length_ourP.json");

        harvest("stadium", null, KgUnit.DIMENSIONLESS,
                true, 0.9, 10,
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity", 200,
                "./eval/qkbc/exp_1/qsearch_queries/stadium_capacity_ourP.json");

        // Bootstrapping - Non-parametric
        harvest("building", null, KgUnit.getKgUnitFromEntityName("<Metre>"),
                false, 0.9, 10,
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height", 200,
                "./eval/qkbc/exp_1/qsearch_queries/building_height_ourN.json");

        harvest("mountain", null, KgUnit.getKgUnitFromEntityName("<Metre>"),
                false, 0.9, 10,
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation", 200,
                "./eval/qkbc/exp_1/qsearch_queries/mountain_elevation_ourN.json");

//        harvest("river", null, KgUnit.getKgUnitFromEntityName("<Metre>"),
//                false, 0.9, 10,
//                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length", 200,
//                "./eval/qkbc/exp_1/qsearch_queries/river_length_ourN.json");

        harvest("stadium", null, KgUnit.DIMENSIONLESS,
                false, 0.9, 10,
                "./eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity", 200,
                "./eval/qkbc/exp_1/qsearch_queries/stadium_capacity_ourN.json");

        // Original - parametric
        harvest("city", "altitude", KgUnit.getKgUnitFromEntityName("<Metre>"),
                true, 0.9, 10,
                null, 200,
                "./eval/qkbc/exp_1/qsearch_queries/city_altitude_ourP.json");
        harvest("company", "reported revenue", KgUnit.getKgUnitFromEntityName("<United_States_dollar>"),
                true, 0.9, 10,
                null, 200,
                "./eval/qkbc/exp_1/qsearch_queries/company_revenue_ourP.json");

        // Original - non - parametric
        harvest("city", "altitude", KgUnit.getKgUnitFromEntityName("<Metre>"),
                false, 0.9, 10,
                null, 200,
                "./eval/qkbc/exp_1/qsearch_queries/city_altitude_ourN.json");

        harvest("company", "reported revenue", KgUnit.getKgUnitFromEntityName("<United_States_dollar>"),
                false, 0.9, 10,
                null, 200,
                "./eval/qkbc/exp_1/qsearch_queries/company_revenue_ourN.json");
    }
}
