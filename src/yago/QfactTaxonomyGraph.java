package yago;

import model.quantity.Quantity;
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

    private ArrayList<ArrayList<Pair<String, Quantity>>> entityQfactLists;
    private ArrayList<ArrayList<Pair<Integer, Integer>>> taxonomyEntityWithQfactLists; // Pair<entityId, distance>

    private void exploreTypeIds(int id, int currentDist, int distLimit, HashMap<Integer, Integer> typeId2Dist) {
        if (currentDist > distLimit || typeId2Dist.containsKey(id)) {
            return;
        }
        typeId2Dist.put(id, currentDist);
        for (int v : typeDadLists.get(id)) {
            exploreTypeIds(v, currentDist + 1, distLimit, typeId2Dist);
        }
    }

    public QfactTaxonomyGraph(String qfactFile) {
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
            HashMap<Integer, Integer> typeId2Dist = new HashMap<>();
            for (int v : entityTypeLists.get(i)) {
                exploreTypeIds(v, 1, Integer.MAX_VALUE, typeId2Dist);
            }
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
        this(DEFAULT_QFACT_FILE);
    }

    // returns Pair<entityId, distance>, sorted by distance
    public ArrayList<Pair<Integer, Integer>> getSimilarEntityIdsWithQfact(String entity, int distanceLimit) {
        // Go up to get type list.
        Integer entityId = entity2Id.get(entity);
        if (entityId == null) {
            return null;
        }
        HashMap<Integer, Integer> typeId2Distance = new HashMap<>();
        for (int v : entityTypeLists.get(entityId)) {
            exploreTypeIds(v, 1, distanceLimit - 1, typeId2Distance);
        }
        HashMap<Integer, Integer> entityId2Distance = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : typeId2Distance.entrySet()) {
            ArrayList<Pair<Integer, Integer>> entitiesWithQfact = taxonomyEntityWithQfactLists.get(e.getKey());
            if (entitiesWithQfact == null) {
                continue;
            }
            for (Pair<Integer, Integer> p : entitiesWithQfact) {
                if (p.second > distanceLimit - e.getValue()) {
                    break;
                }
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
    public ArrayList<Pair<String, Integer>> getSimilarEntitiesWithQfact(String entity, int distanceLimit) {
        return getSimilarEntityIdsWithQfact(entity, distanceLimit).stream().map(o -> {
            return new Pair<>(id2Entity.get(o.first), o.second);
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    public static void main(String[] args) {
        QfactTaxonomyGraph graph = new QfactTaxonomyGraph();
        System.out.println(graph.getSimilarEntitiesWithQfact("<Cristiano_Ronaldo>", 2).size());
        System.out.println(graph.getSimilarEntitiesWithQfact("<Cristiano_Ronaldo>", 3).size());
        System.out.println(graph.getSimilarEntitiesWithQfact("<Cristiano_Ronaldo>", 4).size());
        System.out.println(graph.getSimilarEntitiesWithQfact("<Cristiano_Ronaldo>", 5).size());
        System.out.println(graph.getSimilarEntitiesWithQfact("<Cristiano_Ronaldo>", 10).size());

        System.out.println(graph.getSimilarEntitiesWithQfact("<Barack_Obama>", 2).size());
        System.out.println(graph.getSimilarEntitiesWithQfact("<Barack_Obama>", 3).size());
        System.out.println(graph.getSimilarEntitiesWithQfact("<Barack_Obama>", 4).size());
        System.out.println(graph.getSimilarEntitiesWithQfact("<Barack_Obama>", 5).size());
        System.out.println(graph.getSimilarEntitiesWithQfact("<Barack_Obama>", 10).size());
    }
}
