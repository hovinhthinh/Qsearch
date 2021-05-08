package qkbc.text;

import model.quantity.kg.KgUnit;
import nlp.NLP;
import qkbc.distribution.DistributionFitter;
import server.text.ResultInstance;
import server.text.handler.search.SearchResult;
import shaded.org.apache.http.client.utils.URIBuilder;
import uk.ac.susx.informatics.Morpha;
import umontreal.ssj.probdist.ContinuousDistribution;
import umontreal.ssj.probdist.EmpiricalDist;
import util.Number;
import util.*;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

class ContextStats {
    private HashMap<String, List<Double>> entity2Values = new HashMap<>();
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

    public void addInstance(String entity, double quantityStdValue) {
        if (!entity2Values.containsKey(entity)) {
            entity2Values.put(entity, new ArrayList<>());
        }
        for (Double v : entity2Values.get(entity)) {
            if (Number.relativeNumericDistance(v, quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                return;
            }
        }
        entity2Values.get(entity).add(quantityStdValue);
    }

    public int support() {
        return entity2ValuesDuplicated.entrySet().stream().mapToInt(o -> o.getValue().size()).sum();
    }

    public double extensibility() {
        int sup = support();
        int total = entity2Values.entrySet().stream().mapToInt(o -> o.getValue().size()).sum();
        return 1 - ((double) sup) / total;
    }

    public double confidence(ContinuousDistribution positiveDist) {
        return entity2Values.entrySet().stream().flatMap(o -> o.getValue().stream())
                .mapToDouble(o -> DistributionFitter.getPValueFromSample(positiveDist, o)).average().getAsDouble();
    }
}

public class QKBCRunner {
    public static final String QSEARCH_END_POINT = "http://sedna:6993/kbc_text";

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
                    result.add(new RelationInstance(ri.entity, si.quantity, si.quantityStandardValue, si.score, si.kbcId));
                }
            }

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static void printToFile(List<RelationInstance> instances, File outputFile) {
        PrintWriter out = FileUtils.getPrintWriter(outputFile, StandardCharsets.UTF_8);
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

        out.close();
    }

    public static void harvest(String type, String seedCtx, KgUnit quantitySiUnit,
                               double groupConfidenceThreshold, File outputFile) {
        HashMap<String, RelationInstance> kbcId2RelationInstanceMap = new HashMap<>();

        ArrayList<RelationInstance> riList = new ArrayList<>();
        LinkedHashSet<String> ctxList = new LinkedHashSet<>();

        Queue<String> ctxQueue = new LinkedList<>() {{
            add(seedCtx);
        }};

        int iter = 0;
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
                    storedI.score = Math.min(storedI.score, i.score);
                }
            }
            // query reformulation mining
            ArrayList<RelationInstance> mostlyPositive = riList.stream()
                    .filter(i -> 1 / i.score >= groupConfidenceThreshold)
                    .collect(Collectors.toCollection(ArrayList::new));
            Pair<ContinuousDistribution, Double> positiveDist = RelationInstanceNoiseFilter.consistencyBasedDistributionNoiseFilter(mostlyPositive);

            // print stats
            System.out.println(String.format("Mostly-positive size: %d", mostlyPositive.size()));
            System.out.println(String.format("Noise: %d", mostlyPositive.stream().filter(i -> !i.positive).count()));
            for (RelationInstance i : mostlyPositive) {
                if (!i.positive) {
                    System.out.println(String.format("  %s  |  %s  |  %s  |  %s",
                            i.entity, i.quantity, i.getSentence(), i.getSource()));
                }
            }
            List<RelationInstance> positivePart = mostlyPositive.stream().filter(i -> i.positive).collect(Collectors.toList());

            EmpiricalDist empDist = new EmpiricalDist(RelationInstanceNoiseFilter.extractDistributionSamplesFromRelationInstances(positivePart)
                    .stream().mapToDouble(Double::doubleValue).toArray());

            System.out.println(String.format("Positive samples: size: %d/%d (%d entities) | mean: %.3f | sd: %.3f",
                    positivePart.size(), riList.size(), positivePart.stream().collect(Collectors.groupingBy(o -> o.entity)).size(),
                    empDist.getMean(), empDist.getStandardDeviation()));


            System.out.println(String.format("Positive distribution: %s | mean: %.3f | sd: %.3f | p-value: %.3f",
                    positiveDist.first.toString(), positiveDist.first.getMean(), positiveDist.first.getStandardDeviation(), positiveDist.second));

            // mine more context in the unknown part
            Map<String, List<RelationInstance>> entity2PositiveInstances = riList.stream().filter(i -> i.positive)
                    .collect(Collectors.groupingBy(i -> i.entity));

            ArrayList<RelationInstance> unknownInstances = riList.stream().filter(i -> !i.positive).collect(Collectors.toCollection(ArrayList::new));

            HashMap<String, ContextStats> contextStats = new HashMap<>();

            for (RelationInstance u : unknownInstances) {
                ArrayList<String> ctx = u.getNormalContext();
                // stemming & unique
                ctx = NLP.splitSentence(NLP.fastStemming(String.join(" ", ctx).toLowerCase(), Morpha.any));
                ctx = new HashSet<>(ctx).stream().collect(Collectors.toCollection(ArrayList::new));
                // uni-gram
                loop:
                for (String x : ctx) {
                    if (!contextStats.containsKey(x)) {
                        contextStats.put(x, new ContextStats(x));
                    }
                    ContextStats stats = contextStats.get(x);
                    // add unknown
                    stats.addInstance(u.entity, u.quantityStdValue);
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
                        stats.addInstance(u.entity, u.quantityStdValue);
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


            // Sort stats and output
            List<ContextStats> sortedContextStats = contextStats.entrySet().stream().map(e -> e.getValue())
                    .filter(o -> o.support() > 1
                            && o.confidence(positiveDist.first) >= 0.4
                            && o.extensibility() > 0)
                    .sorted((a, b) -> Long.compare(b.support(), a.support()))
                    .collect(Collectors.toList());

            System.out.println("Mined context: " + sortedContextStats.size());
            for (ContextStats stats : sortedContextStats) {
                System.out.println(String.format("  ContextStats{'%s', support=%d, extensibility=%.3f, confidence=%.3f}",
                        stats.context, stats.support(), stats.extensibility(), stats.confidence(positiveDist.first)));
            }

//            DistributionFitter.drawDistributionVsSamples(String.format("Iteration #%d", iter), positiveDist.first,
//                    positivePart.stream().mapToDouble(i -> i.quantityStdValue).toArray(), true);

            // reformulate
            for (ContextStats stats : sortedContextStats) {
                if (!ctxList.contains(stats.context)) {
                    ctxQueue.add(stats.context);
//                    break;
                }
            }

            if (outputFile != null && ctxQueue.isEmpty()) {
                printToFile(positivePart, outputFile);
            }
        } while (!ctxQueue.isEmpty());
    }

    public static void main(String[] args) {
//        harvest("building", "height", KgUnit.getKgUnitFromEntityName("<Metre>"), 0.9,
//        new File("./eval/qkbc/exp_1/qsearch_queries/building_height.tsv"));

        harvest("tunnel", "length", KgUnit.getKgUnitFromEntityName("<Metre>"), 0.9,
                new File("./eval/qkbc/exp_1/qsearch_queries/tunnel_length.tsv"));
    }
}
