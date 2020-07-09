package pipeline.table;

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
import yago.TaxonomyGraph;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class QfactTaxonomyGraph {
    public static final Logger LOGGER = Logger.getLogger(QfactTaxonomyGraph.class.getName());

    // The order of the lines are important (they are IDs).
    public static final String QFACT_FILE = "table-non-deep/qfact_text_coref.gz";

    public static int DEFAULT_RELATED_ENTITY_DIST_LIM = 4;
    public static int NTOP_RELATED_ENTITY = 5;
    public static double QFACT_CONTEXT_MATCH_WEIGHT = 0.9; // quantity match weight = 1 - this weight.

    public static double TYPE_RELATED_PENALTY_WEIGHT = 0;

    private static QfactTaxonomyGraph DEFAULT_GRAPH;

    public static QfactTaxonomyGraph getDefaultGraphInstance() {
        if (DEFAULT_GRAPH == null) {
            DEFAULT_GRAPH = new QfactTaxonomyGraph();
        }
        return DEFAULT_GRAPH;
    }

    public static class EntityTextQfact {
        int id;
        String entity;
        ArrayList<String> context;
        Quantity quantity;
        String sentence;
        String source;
        String referSentence;

        @Override
        public String toString() {
            return String.format("%s\t%s\t%s\t%s", entity, sentence, source, referSentence);
        }
    }

    public static HashMap<Integer, EntityTextQfact> loadBackgroundTextQfactMap() {
        HashMap<Integer, EntityTextQfact> map = new HashMap<>();
        int id = 0;
        for (String line : FileUtils.getLineStream(QFACT_FILE, "UTF-8")) {
            String[] arr = line.split("\t");
            EntityTextQfact qfact = new EntityTextQfact();
            qfact.id = id++;
            qfact.entity = arr[0];
            qfact.quantity = Quantity.fromQuantityString(arr[2]);
            if (QuantityDomain.getDomain(qfact.quantity).equals(QuantityDomain.Domain.DIMENSIONLESS)) {
                arr[1] += " " + qfact.quantity.unit;
            }
            qfact.context = NLP.splitSentence(arr[1].toLowerCase());
            qfact.context.trimToSize();

            qfact.sentence = arr[3];
            qfact.source = arr[4];
            qfact.referSentence = arr[5];

            map.put(qfact.id, qfact);
        }
        return map;
    }

    private ArrayList<EntityTextQfact>[] entityQfactLists;
    private ArrayList<Pair<Integer, Integer>>[] taxonomyEntityWithQfactLists; // Pair<entityId, distance>, order by distance

    private int relatedEntityDistanceLimit;

    private Long2ObjectOpenHashMap<Pair<Double, EntityTextQfact>> cache = new Long2ObjectOpenHashMap<>(10000000);

    public TaxonomyGraph taxonomy;

    public void resetCache() {
        cache.clear();
    }

    public QfactTaxonomyGraph(int relatedEntityDistanceLimit) {
        this.taxonomy = TaxonomyGraph.getDefaultGraphInstance();
        this.relatedEntityDistanceLimit = relatedEntityDistanceLimit;
        LOGGER.info("Loading YAGO Qfact taxonomy graph");
        entityQfactLists = new ArrayList[taxonomy.nEntities];
        for (int i = 0; i < taxonomy.nEntities; ++i) {
            entityQfactLists[i] = new ArrayList<>();
        }
        for (EntityTextQfact qfact : loadBackgroundTextQfactMap().values()) {
            Integer entityId = taxonomy.entity2Id.get(qfact.entity);
            if (entityId == null) {
                continue;
            }
            entityQfactLists[entityId].add(qfact);
        }

        LOGGER.info("Populating entities with Qfact to taxonomy.");
        taxonomyEntityWithQfactLists = new ArrayList[taxonomy.nTypes];
        for (int i = 0; i < taxonomy.nTypes; ++i) {
            taxonomyEntityWithQfactLists[i] = new ArrayList<>();
        }
        for (int i = 0; i < taxonomy.nEntities; ++i) {
            ArrayList<EntityTextQfact> qfacts = entityQfactLists[i];
            if (qfacts.size() == 0) {
                entityQfactLists[i] = null;
                continue;
            }
            qfacts.trimToSize();

            // populate for a single entity
            Int2IntLinkedOpenHashMap typeId2Dist = taxonomy.getType2DistanceMapForEntityWithCache(i);
            for (Int2IntMap.Entry t : Int2IntMaps.fastIterable(typeId2Dist)) {
                taxonomyEntityWithQfactLists[t.getIntKey()].add(new Pair<>(i, t.getIntValue()));
            }
        }
        for (int i = 0; i < taxonomy.nTypes; ++i) {
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
        this(DEFAULT_RELATED_ENTITY_DIST_LIM);
    }

    // returns Pair<entityId, agreement with key entity>
    public HashMap<Integer, Double> getSimilarEntityIdsWithQfact(int entityId) {
        // Go up to get type list.
        Int2IntLinkedOpenHashMap typeId2Distance = taxonomy.getType2DistanceMapForEntityWithCache(entityId);
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
                if (entityId2Itf.getOrDefault(p.first, 0.0) < taxonomy.type2Itf[e.getIntKey()]) {
                    entityId2Itf.put(p.first, taxonomy.type2Itf[e.getIntKey()]);
                }
            }
        }
        return entityId2Itf;
    }

    // returns Pair<entity, itf>
    public ArrayList<Pair<String, Double>> getSimilarEntitiesWithQfact(int entityId) {
        return getSimilarEntityIdsWithQfact(entityId).entrySet().stream().map(
                o -> new Pair<>(taxonomy.id2Entity.get(o.getKey()), o.getValue())
        ).collect(Collectors.toCollection(ArrayList::new));
    }

    // returns Pair<score, matchedQfactStr>
    // returns null of cannot match.
    public Pair<Double, EntityTextQfact> getMatchScore(String entity, String context, Quantity quantity, int key) {
        Integer entityId = taxonomy.entity2Id.get(entity);
        if (entityId == null) {
            return null;
        }
        long globalKey = 1000000000L * entityId + key;
        Pair<Double, EntityTextQfact> result = cache.get(globalKey);
        if (result != null) {
            return result.second == null ? null : result;
        }

        String thisDomain = QuantityDomain.getDomain(quantity);
        if (thisDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            context = context + " " + quantity.unit;
        }
        ArrayList<String> thisContext = NLP.splitSentence(context.toLowerCase());

        ObjectHeapPriorityQueue<Pair<Double, EntityTextQfact>> queue = new ObjectHeapPriorityQueue<>((a, b) -> {
            int i = a.first.compareTo(b.first);
            if (i != 0) {
                return i;
            }
            if (a.second != null && entity.equals(a.second.entity)) {
                return 1;
            } else if (b.second != null && entity.equals(b.second.entity)) {
                return -1;
            } else {
                return 0;
            }
        });

        // match exact entity
        ArrayList<EntityTextQfact> qfacts = entityQfactLists[entityId];
        if (qfacts != null) {
            Pair<Double, EntityTextQfact> singleEntityResult = new Pair<>(0.0, null);
            for (EntityTextQfact o : qfacts) {
                // different concept should be ignored
                if (!thisDomain.equals(QuantityDomain.getDomain(o.quantity))) {
                    continue;
                }

                double contextMatchScr = ContextEmbeddingMatcher.directedEmbeddingIdfSimilarity(thisContext, o.context);
                double quantityMatchScr = quantity.compareTo(o.quantity) == 0 ? 1 : 0;

                double matchScr = contextMatchScr * QFACT_CONTEXT_MATCH_WEIGHT + quantityMatchScr * (1 - QFACT_CONTEXT_MATCH_WEIGHT);
                if (singleEntityResult.second == null || matchScr > singleEntityResult.first) {
                    singleEntityResult.first = matchScr;
                    singleEntityResult.second = o;
                }
            }
            queue.enqueue(singleEntityResult);
        }
        // match related entities
        HashMap<Integer, Double> relatedEntities = getSimilarEntityIdsWithQfact(entityId);
        if (relatedEntities != null) {
            double itfScalingFactor = Math.log10((taxonomy.nEntities - 0.5) / 1.5);
            for (Map.Entry<Integer, Double> p : relatedEntities.entrySet()) { // contains eId & itf
                qfacts = entityQfactLists[p.getKey()];

                long localKey = -(1000000000L * (p.getKey() + 1) + key);
                Pair<Double, EntityTextQfact> singleEntityResult = cache.get(localKey);
                if (singleEntityResult == null) {
                    singleEntityResult = new Pair<>(0.0, null);
                    for (EntityTextQfact o : qfacts) {
                        // different concept should be ignored
                        if (!thisDomain.equals(QuantityDomain.getDomain(o.quantity))) {
                            continue;
                        }
                        double contextMatchScr = ContextEmbeddingMatcher.directedEmbeddingIdfSimilarity(thisContext, o.context);

                        double matchScr = contextMatchScr * QFACT_CONTEXT_MATCH_WEIGHT;
                        if (singleEntityResult.second == null || matchScr > singleEntityResult.first) {
                            singleEntityResult.first = matchScr;
                            singleEntityResult.second = o;
                        }
                    }
                    if (key >= 0) {
                        cache.put(localKey, singleEntityResult);
                    }
                }

                // clone IMPORTANT!
                singleEntityResult = new Pair<>(singleEntityResult.first, singleEntityResult.second);

                // penalty for type-related matching
                singleEntityResult.first *= Math.pow(p.getValue() / itfScalingFactor, TYPE_RELATED_PENALTY_WEIGHT);
                queue.enqueue(singleEntityResult);
                // keep only top related entities
                if (queue.size() > NTOP_RELATED_ENTITY) {
                    queue.dequeue();
                }
            }
        }

        result = new Pair<>(0.0, null);
        while (!queue.isEmpty()) {
            Pair<Double, EntityTextQfact> top = queue.dequeue();
            result.first += top.first / NTOP_RELATED_ENTITY;
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
        QfactTaxonomyGraph graph = getDefaultGraphInstance();
    }
}
