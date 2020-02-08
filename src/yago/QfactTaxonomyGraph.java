package yago;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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

    // returns Pair<entityId, itf>
    public HashMap<Integer, Double> getSimilarEntityIdsWithQfact(String entity) {
        // Go up to get type list.
        Integer entityId = entity2Id.get(entity);
        if (entityId == null) {
            return null;
        }
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
                // Update itf
                if (entityId2Itf.getOrDefault(p.first, 0.0) < type2Itf[e.getIntKey()]) {
                    entityId2Itf.put(p.first, type2Itf[e.getIntKey()]);
                }
            }
        }
        return entityId2Itf;
    }

    // returns Pair<entity, itf>
    public ArrayList<Pair<String, Double>> getSimilarEntitiesWithQfact(String entity) {
        return getSimilarEntityIdsWithQfact(entity).entrySet().stream().map(
                o -> new Pair<>(id2Entity.get(o.getKey()), o.getValue())
        ).collect(Collectors.toCollection(ArrayList::new));
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
        ArrayList<Pair<String, Quantity>> qfacts = entityQfactLists[entityId];
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

        HashMap<Integer, Double> relatedEntities = getSimilarEntityIdsWithQfact(entity);
        if (relatedEntities == null) {
            return null;
        }

        String thisDomain = QuantityDomain.getDomain(quantity);
        if (thisDomain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            context = context + " " + quantity.unit;
        }
        double score = 0;
        String matchStr = null;

        for (Map.Entry<Integer, Double> p : relatedEntities.entrySet()) { // contains eId & itf
            ArrayList<Pair<String, Quantity>> qfacts = entityQfactLists[p.getKey()];
            if (qfacts == null) {
                continue;
            }
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
            // scaling with itf
//            singleEntityResult.first *= Math.pow(p.getValue() + 1, 0);
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
