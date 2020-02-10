package yago;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import model.context.ContextEmbeddingMatcher;
import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import nlp.NLP;
import util.FileUtils;
import util.Pair;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class QfactTaxonomyGraph extends TaxonomyGraph {
    public static final Logger LOGGER = Logger.getLogger(QfactTaxonomyGraph.class.getName());
    public static final String DEFAULT_QFACT_FILE = "./non-deep/qfact_text.gz";

    public static final int DEFAULT_RELATED_ENTITY_DIST_LIM = 4;
    public static final int NTOP_RELATED_ENTITY = 3;
    public static final double QFACT_CONTEXT_MATCH_WEIGHT = 0.9; // quantity match weight = 1 - this weight.

    private ArrayList<Pair<String, Quantity>>[] entityQfactLists;
    private ArrayList<Pair<Integer, Integer>>[] taxonomyEntityWithQfactLists; // Pair<entityId, distance>, order by distance

    private ContextEmbeddingMatcher matcher = new ContextEmbeddingMatcher(-1); // alpha not used
    private int relatedEntityDistanceLimit;

    private Long2ObjectOpenHashMap<Pair<Double, String>> cache = new Long2ObjectOpenHashMap<>(10000000);

    public void resetCache() {
        cache.clear();
    }

    public QfactTaxonomyGraph(String qfactFile, int relatedEntityDistanceLimit) {
        this.relatedEntityDistanceLimit = relatedEntityDistanceLimit;
        LOGGER.info("Loading YAGO Qfact taxonomy graph");
        entityQfactLists = new ArrayList[nEntities];
        for (int i = 0; i < nEntities; ++i) {
            entityQfactLists[i] = new ArrayList<>();
        }
        for (String line : FileUtils.getLineStream(qfactFile, "UTF-8")) {
            String[] arr = line.split("\t");
            Integer entityId = entity2Id.get(arr[0]);
            if (entityId == null) {
                continue;
            }
            entityQfactLists[entityId].add(new Pair<>(arr[1], Quantity.fromQuantityString(arr[2])));
        }

        LOGGER.info("Populating entities with Qfact to taxonomy.");
        taxonomyEntityWithQfactLists = new ArrayList[nTypes];
        for (int i = 0; i < nTypes; ++i) {
            taxonomyEntityWithQfactLists[i] = new ArrayList<>();
        }
        for (int i = 0; i < nEntities; ++i) {
            ArrayList<Pair<String, Quantity>> qfacts = entityQfactLists[i];
            if (qfacts.size() == 0) {
                entityQfactLists[i] = null;
                continue;
            }
            qfacts.trimToSize();

            // populate for a single entity
            Int2IntLinkedOpenHashMap typeId2Dist = getType2DistanceMapForEntity(i);
            for (Int2IntMap.Entry t : Int2IntMaps.fastIterable(typeId2Dist)) {
                taxonomyEntityWithQfactLists[t.getIntKey()].add(new Pair<>(i, t.getIntValue()));
            }
        }
        for (int i = 0; i < nTypes; ++i) {
            ArrayList<Pair<Integer, Integer>> entitiesWithQfact = taxonomyEntityWithQfactLists[i];
            if (entitiesWithQfact.size() == 0) {
                taxonomyEntityWithQfactLists[i] = null;
                continue;
            }
            entitiesWithQfact.trimToSize();
            // sort by distance
            Collections.sort(entitiesWithQfact, Comparator.comparing(o -> o.second));
        }
    }

    public QfactTaxonomyGraph() {
        this(DEFAULT_QFACT_FILE, DEFAULT_RELATED_ENTITY_DIST_LIM);
    }

    // returns Pair<entityId, agreement with key entity>
    public HashMap<Integer, Double> getSimilarEntityIdsWithQfact(int entityId) {
        // Go up to get type list.
        Int2IntLinkedOpenHashMap typeId2Distance = getType2DistanceMapForEntity(entityId);
        HashMap<Integer, Double> entityId2Itf = new HashMap<>();
        for (Int2IntMap.Entry e : typeId2Distance.int2IntEntrySet()) {
            if (e.getIntValue() >= relatedEntityDistanceLimit) {
                break;
            }
            ArrayList<Pair<Integer, Integer>> entitiesWithQfact = taxonomyEntityWithQfactLists[e.getIntKey()];
            if (entitiesWithQfact == null) {
                continue;
            }
            for (Pair<Integer, Integer> p : entitiesWithQfact) {
                if (p.second > relatedEntityDistanceLimit - e.getIntValue()) {
                    break;
                }
                // Update agreement (now using Itf)
                if (entityId2Itf.getOrDefault(p.first, 0.0) < type2Itf[e.getIntKey()]) {
                    entityId2Itf.put(p.first, type2Itf[e.getIntKey()]);
                }
            }
        }
        return entityId2Itf;
    }

    // returns Pair<entity, itf>
    public ArrayList<Pair<String, Double>> getSimilarEntitiesWithQfact(int entityId) {
        return getSimilarEntityIdsWithQfact(entityId).entrySet().stream().map(
                o -> new Pair<>(id2Entity.get(o.getKey()), o.getValue())
        ).collect(Collectors.toCollection(ArrayList::new));
    }

    // returns Pair<score, matchedQfactStr>
    // returns null of cannot match.
    public Pair<Double, String> getMatchScore(String entity, String context, Quantity quantity, int key) {
        Integer entityId = entity2Id.get(entity);
        if (entityId == null) {
            return null;
        }
        long globalKey = 1000000000L * entityId + key;
        Pair<Double, String> result = cache.get(globalKey);
        if (result != null) {
            return result;
        }

        String thisDomain = QuantityDomain.getDomain(quantity);
        if (thisDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            context = context + " " + quantity.unit;
        }
        ArrayList<String> thisContext = NLP.splitSentence(context.toLowerCase());

        ObjectHeapPriorityQueue<Pair<Double, String>> queue = new ObjectHeapPriorityQueue<>(Comparator.comparing(o -> o.first));

        // match exact entitiy
        ArrayList<Pair<String, Quantity>> qfacts = entityQfactLists[entityId];
        if (qfacts != null) {
            Pair<Double, String> singleEntityResult = new Pair<>(0.0, null);
            for (Pair<String, Quantity> o : qfacts) {
                // different concept should be ignored
                if (!thisDomain.equals(QuantityDomain.getDomain(o.second))) {
                    continue;
                }
                String oContext = o.first;
                if (thisDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
                    oContext += " " + o.second.unit;
                }

                double contextMatchScr = matcher.directedEmbeddingIdfSimilarity(thisContext, NLP.splitSentence(oContext.toLowerCase()));
                double quantityMatchScr = quantity.compareTo(o.second) == 0 ? 1 : 0;

                double matchScr = contextMatchScr * QFACT_CONTEXT_MATCH_WEIGHT + quantityMatchScr * (1 - QFACT_CONTEXT_MATCH_WEIGHT);
                if (matchScr > singleEntityResult.first) {
                    singleEntityResult.first = matchScr;
                    singleEntityResult.second = entity + "\t" + o.first + "\t" + o.second.toString();
                }
            }
            queue.enqueue(singleEntityResult);
        }
        // match related entities
        HashMap<Integer, Double> relatedEntities = getSimilarEntityIdsWithQfact(entityId);
        if (relatedEntities != null) {
            for (Map.Entry<Integer, Double> p : relatedEntities.entrySet()) { // contains eId & itf
                qfacts = entityQfactLists[p.getKey()];

                long localKey = -(1000000000L * (p.getKey() + 1) + key);
                Pair<Double, String> singleEntityResult = cache.get(localKey);
                if (singleEntityResult == null) {
                    singleEntityResult = new Pair<>(0.0, null);
                    for (Pair<String, Quantity> o : qfacts) {
                        // different concept should be ignored
                        if (!thisDomain.equals(QuantityDomain.getDomain(o.second))) {
                            continue;
                        }
                        String oContext = o.first;
                        if (thisDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
                            oContext += " " + o.second.unit;
                        }

                        double contextMatchScr = matcher.directedEmbeddingIdfSimilarity(thisContext, NLP.splitSentence(oContext.toLowerCase()));

                        double matchScr = contextMatchScr * QFACT_CONTEXT_MATCH_WEIGHT;
                        if (matchScr > singleEntityResult.first) {
                            singleEntityResult.first = matchScr;
                            singleEntityResult.second = entity + "\t" + o.first + "\t" + o.second.toString();
                        }
                    }
                    if (key >= 0) {
                        cache.put(localKey, singleEntityResult);
                    }
                }
                // TODO: scaling for type-related matching
//            singleEntityResult.first *= Math.pow(p.getValue() + 1, 0);
                queue.enqueue(singleEntityResult);
                // sum of top 5 related entities
                // TODO: fix this const
                if (queue.size() > NTOP_RELATED_ENTITY) {
                    queue.dequeue();
                }
            }
        }

        result = new Pair<>(0.0, null);
        int queueSize = queue.size();
        while (!queue.isEmpty()) {
            Pair<Double, String> top = queue.dequeue();
            result.first += top.first / queueSize;
            if (queue.isEmpty()) {
                result.second = top.second;
            }
        }

        if (key >= 0) {
            cache.put(globalKey, result);
        }

        if (result.second == null) {
            return null;
        }
        return result;
    }

    public static void main(String[] args) {
        QfactTaxonomyGraph graph = new QfactTaxonomyGraph();
    }
}
