package qkbc.text;

import model.quantity.kg.KgUnit;
import nlp.NLP;
import qkbc.DistributionFitter;
import server.text.ResultInstance;
import server.text.handler.search.SearchResult;
import shaded.org.apache.http.client.utils.URIBuilder;
import uk.ac.susx.informatics.Morpha;
import umontreal.ssj.probdist.ContinuousDistribution;
import util.Crawler;
import util.Gson;
import util.Number;
import util.Pair;

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
            if (Number.relativeNumericDistance(v, quantityStdValue) <= 0.01) {
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
            if (Number.relativeNumericDistance(v, quantityStdValue) <= 0.01) {
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

    public static void harvest(String type, String seedCtx, KgUnit quantitySiUnit) {
        double groupConfidenceThreshold = 0.85;

        HashMap<String, RelationInstance> kbcId2RelationInstanceMap = new HashMap<>();

        ArrayList<RelationInstance> riList = new ArrayList<>();
        ArrayList<String> ctxList = new ArrayList<>();

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
            ArrayList<RelationInstance> mostlyPositive = new ArrayList<>();
            for (RelationInstance i : riList) {
                if (1 / i.score >= groupConfidenceThreshold) {
                    mostlyPositive.add(i);
                }
            }
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
            System.out.println(String.format("Positive size: %d/%d (%d entities)",
                    positivePart.size(), riList.size(), positivePart.stream().collect(Collectors.groupingBy(o -> o.entity)).size()));
            System.out.println(String.format("Distribution: %s | p-value: %.3f", positiveDist.first.toString(), positiveDist.second));

//            DistributionFitter.drawDistributionVsSamples(String.format("Iteration #%d", iter), positiveDist.first,
//                    positivePart.stream().mapToDouble(i -> i.quantityStdValue).toArray(), true);

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
                        if (Number.relativeNumericDistance(u.quantityStdValue, p.quantityStdValue) <= 0.01) {
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
                            if (Number.relativeNumericDistance(u.quantityStdValue, p.quantityStdValue) <= 0.01) {
                                stats.addDuplicatedInstance(u.entity, u.quantityStdValue);
                                continue loop;
                            }
                        }
                    }
                }
            }


            // Sort stats and output
            List<ContextStats> sortedContextStats = contextStats.entrySet().stream().map(e -> e.getValue())
                    .filter(o -> o.support() > 1 && o.confidence(positiveDist.first) >= 0.3)
                    .sorted((a, b) -> Long.compare(b.support(), a.support()))
                    .collect(Collectors.toList());

            System.out.println("Mined context: " + sortedContextStats.size());
            for (ContextStats stats : sortedContextStats) {
                System.out.println(String.format("  ContextStats{'%s', support=%d, extensibility=%.3f, confidence=%.3f}",
                        stats.context, stats.support(), stats.extensibility(), stats.confidence(positiveDist.first)));
            }

            // reformulate
            if (sortedContextStats.size() > 0) {
                ctxQueue.add(sortedContextStats.get(0).context);
            }
        } while (!ctxQueue.isEmpty());
    }

    public static void main(String[] args) {
        harvest("building", "height", KgUnit.getKgUnitFromEntityName("<Metre>"));
    }
}
