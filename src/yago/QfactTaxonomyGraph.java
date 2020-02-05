package yago;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import model.context.ContextEmbeddingMatcher;
import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import nlp.NLP;
import util.FileUtils;
import util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class QfactTaxonomyGraph extends TaxonomyGraph {
    public static final Logger LOGGER = Logger.getLogger(QfactTaxonomyGraph.class.getName());
    public static final String DEFAULT_QFACT_FILE = "./non-deep/qfact_text.gz";
    public static final int DEFAULT_RELATED_ENTITY_DIST_LIM = 4;
    private ArrayList<ArrayList<Pair<String, Quantity>>> entityQfactLists;
    private ArrayList<ArrayList<Pair<Integer, Integer>>> taxonomyEntityWithQfactLists; // Pair<entityId, distance>

    private ContextEmbeddingMatcher matcher = new ContextEmbeddingMatcher(-1); // alpha not used
    private int relatedEntityDistanceLimit;

    private Long2ObjectOpenHashMap<Pair<Double, String>> cache = new Long2ObjectOpenHashMap<>(10000000);

    public void resetCache() {
        cache.clear();
    }

    public QfactTaxonomyGraph(String qfactFile, int relatedEntityDistanceLimit) {
        this.relatedEntityDistanceLimit = relatedEntityDistanceLimit;
        LOGGER.info("Loading YAGO Qfact taxonomy graph");
        entityQfactLists = new ArrayList<>(nEntities);
        for (int i = 0; i < nEntities; ++i) {
            entityQfactLists.add(new ArrayList<>());
        }
        for (String line : FileUtils.getLineStream(qfactFile, "UTF-8")) {
            String[] arr = line.split("\t");
            Integer entityId = entity2Id.get(arr[0]);
            if (entityId == null) {
                continue;
            }
            entityQfactLists.get(entityId).add(new Pair<>(arr[1], Quantity.fromQuantityString(arr[2])));
        }

        LOGGER.info("Populating entities with Qfact to taxonomy.");
        taxonomyEntityWithQfactLists = new ArrayList<>(nTypes);
        for (int i = 0; i < nTypes; ++i) {
            taxonomyEntityWithQfactLists.add(new ArrayList<>());
        }
        for (int i = 0; i < nEntities; ++i) {
            ArrayList<Pair<String, Quantity>> qfacts = entityQfactLists.get(i);
            if (qfacts.size() == 0) {
                entityQfactLists.set(i, null);
                continue;
            }
            qfacts.trimToSize();

            // populate for a single entity
            HashMap<Integer, Integer> typeId2Dist = getType2DistanceMapForEntity(i, Integer.MAX_VALUE);
            for (Map.Entry<Integer, Integer> e : typeId2Dist.entrySet()) {
                taxonomyEntityWithQfactLists.get(e.getKey()).add(new Pair<>(i, e.getValue()));
            }
        }
        for (int i = 0; i < nTypes; ++i) {
            ArrayList<Pair<Integer, Integer>> entitiesWithQfact = taxonomyEntityWithQfactLists.get(i);
            if (entitiesWithQfact.size() == 0) {
                taxonomyEntityWithQfactLists.set(i, null);
                continue;
            }
            // sort by distance
            Collections.sort(entitiesWithQfact, (a, b) -> a.second.compareTo(b.second));
        }
    }

    public QfactTaxonomyGraph() {
        this(DEFAULT_QFACT_FILE, DEFAULT_RELATED_ENTITY_DIST_LIM);
    }

    // returns Pair<entityId, distance>, sorted by distance
    public ArrayList<Pair<Integer, Integer>> getSimilarEntityIdsWithQfact(String entity) {
        // Go up to get type list.
        Integer entityId = entity2Id.get(entity);
        if (entityId == null) {
            return null;
        }
        HashMap<Integer, Integer> typeId2Distance = getType2DistanceMapForEntity(entityId, relatedEntityDistanceLimit - 1);
        HashMap<Integer, Integer> entityId2Distance = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : typeId2Distance.entrySet()) {
            ArrayList<Pair<Integer, Integer>> entitiesWithQfact = taxonomyEntityWithQfactLists.get(e.getKey());
            if (entitiesWithQfact == null) {
                continue;
            }
            for (Pair<Integer, Integer> p : entitiesWithQfact) {
                if (p.second > relatedEntityDistanceLimit - e.getValue()) {
                    break;
                }
                // Update distance
                int currentDist = entityId2Distance.getOrDefault(p.first, Integer.MAX_VALUE);
                if (currentDist > e.getValue() + p.second) {
                    entityId2Distance.put(p.first, e.getValue() + p.second);
                }
            }
        }
        return entityId2Distance.entrySet().stream().sorted((a, b) -> a.getValue().compareTo(b.getValue())).map(o -> {
            return new Pair<>(o.getKey(), o.getValue());
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    // returns Pair<entity, distance>, sorted by distance
    public ArrayList<Pair<String, Integer>> getSimilarEntitiesWithQfact(String entity) {
        return getSimilarEntityIdsWithQfact(entity).stream().map(o -> {
            return new Pair<>(id2Entity.get(o.first), o.second);
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    // returns Pair<score, matchedQfactStr>
    // returns null of cannot match.
    public Pair<Double, String> getMatchScore(String entity, String context, Quantity quantity, int key) {
        return getTypeMatchScore(entity, context, quantity, key);
    }

    // returns Pair<score, matchedQfactStr>
    // returns null of cannot match.
    public Pair<Double, String> getEntityMatchScore(String entity, String context, Quantity quantity) {
        Integer entityId = entity2Id.get(entity);
        if (entityId == null) {
            return null;
        }
        ArrayList<Pair<String, Quantity>> qfacts = entityQfactLists.get(entityId);
        if (qfacts == null) {
            return null;
        }
        String thisDomain = QuantityDomain.getDomain(quantity);
        if (thisDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            context = context + " " + quantity.unit;
        }
        double score = 0;
        String matchStr = null;
        for (Pair<String, Quantity> o : qfacts) {
            // different concept should be ignored
            if (!thisDomain.equals(QuantityDomain.getDomain(o.second))) {
                continue;
            }
            String oContext = o.first;
            if (thisDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
                oContext += " " + o.second.unit;
            }

            double matchScr = matcher.directedEmbeddingIdfSimilarity(NLP.splitSentence(context.toLowerCase()), NLP.splitSentence(oContext.toLowerCase()));
            if (matchScr > score) {
                score = matchScr;
                matchStr = entity + "\t" + o.first + "\t" + o.second.toString();
            }
        }
        if (matchStr == null) {
            return null;
        }
        return new Pair<>(score, matchStr);
    }

    // returns Pair<score, matchedQfactStr>
    // returns null of cannot match.
    public Pair<Double, String> getTypeMatchScore(String entity, String context, Quantity quantity, int key) {
        Integer entityId = entity2Id.get(entity);
        if (entityId == null) {
            return null;
        }
        long globalKey = 1000000000L * entityId + key;
        Pair<Double, String> result = cache.get(globalKey);
        if (result != null) {
            return result;
        }

        ArrayList<Pair<Integer, Integer>> relatedEntities = getSimilarEntityIdsWithQfact(entity);
        if (relatedEntities == null) {
            return null;
        }

        String thisDomain = QuantityDomain.getDomain(quantity);
        if (thisDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            context = context + " " + quantity.unit;
        }
        double score = 0;
        String matchStr = null;

        for (Pair<Integer, Integer> p : relatedEntities) { // contains eId & distance
            ArrayList<Pair<String, Quantity>> qfacts = entityQfactLists.get(p.first);
            if (qfacts == null) {
                continue;
            }
            long localKey = -(1000000000L * (p.first + 1) + key);
            Pair<Double, String> singleEntityResult;
            if (cache.containsKey(localKey)) {
                singleEntityResult = cache.get(localKey);
            } else {
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

                    double matchScr = matcher.directedEmbeddingIdfSimilarity(NLP.splitSentence(context.toLowerCase()), NLP.splitSentence(oContext.toLowerCase()));
                    if (matchScr > singleEntityResult.first) {
                        singleEntityResult.first = matchScr;
                        singleEntityResult.second = entity + "\t" + o.first + "\t" + o.second.toString();
                    }
                }
                if (key >= 0) {
                    cache.put(localKey, singleEntityResult);
                }
            }
            if (singleEntityResult.first > score) {
                score = singleEntityResult.first;
                matchStr = singleEntityResult.second;
            }
        }

        result = new Pair<>(score, matchStr);
        if (key >= 0) {
            cache.put(globalKey, result);
        }

        if (matchStr == null) {
            return null;
        }
        return result;
    }

    public static void main(String[] args) {
        QfactTaxonomyGraph graph = new QfactTaxonomyGraph();
    }
}
