package yago;

import util.FileUtils;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TaxonomyGraph {
    public static final Logger LOGGER = Logger.getLogger(TaxonomyGraph.class.getName());
    public static final String YAGO_TAXONOMY_FILE = "/GW/D5data-11/hvthinh/yago-taxonomy/yagoTaxonomy.tsv.gz";
    public static final String YAGO_TYPE_FILE = "/GW/D5data-11/hvthinh/yago-taxonomy/yagoTypes.tsv.gz";

    public HashMap<String, Integer> type2Id;
    public ArrayList<String> id2Type;
    public int[] type2nEntities;
    public double[] type2Itf;
    public ArrayList<ArrayList<Integer>> typeDadLists;

    public HashMap<String, Integer> entity2Id;
    public ArrayList<String> id2Entity;
    public ArrayList<ArrayList<Integer>> entityTypeLists;
    public int nEntities;
    public int nTypes;

    public transient LinkedHashSet<Integer>[] cachedEntityTransitiveTypes;

    public int getTypeId(String type, boolean addIfAbsent) {
        Integer id = type2Id.get(type);
        if (id != null) {
            return id;
        }
        if (!addIfAbsent) {
            return -1;
        }
        type2Id.put(type, type2Id.size());
        id2Type.add(type);
        typeDadLists.add(new ArrayList<>());
        return id2Type.size() - 1;
    }


    public int getEntityId(String entity, boolean addIfAbsent) {
        Integer id = entity2Id.get(entity);
        if (id != null) {
            return id;
        }
        if (!addIfAbsent) {
            return -1;
        }
        entity2Id.put(entity, entity2Id.size());
        id2Entity.add(entity);
        entityTypeLists.add(new ArrayList<>());
        return id2Entity.size() - 1;
    }

    public TaxonomyGraph(String yagoTaxonomyFile, String yagoTypeFile) {
        LOGGER.info("Loading YAGO taxonomy graph");
        // Load taxonomy
        type2Id = new HashMap<>();
        id2Type = new ArrayList<>();
        typeDadLists = new ArrayList<>();
        for (String line : FileUtils.getLineStream(yagoTaxonomyFile, "UTF-8")) {
            String[] arr = line.split("\t");
            if (arr.length != 4 || !arr[2].equals("rdfs:subClassOf")) {
                continue;
            }
            String childType = arr[1], parentType = arr[3];
            int childId = getTypeId(childType, true), dadId = getTypeId(parentType, true);
            typeDadLists.get(childId).add(dadId);
        }
        id2Type.trimToSize();
        typeDadLists.trimToSize();
        for (ArrayList<Integer> l : typeDadLists) {
            l.trimToSize();
        }
        nTypes = id2Type.size();
        type2nEntities = new int[nTypes];
        type2Itf = new double[nTypes];

        // Load entity types
        entity2Id = new HashMap<>();
        id2Entity = new ArrayList<>();
        entityTypeLists = new ArrayList<>();
        for (String line : FileUtils.getLineStream(yagoTypeFile, "UTF-8")) {
            String[] arr = line.split("\t");
            if (arr.length != 4 || !arr[2].equals("rdf:type")) {
                continue;
            }
            String entity = arr[1], type = arr[3];
//            String type = NLP.fastStemming(NLP.stripSentence(arr[3].replaceAll("[^A-Za-z0-9]", " ")).toLowerCase(), Morpha.noun);
//            if (type.startsWith("wikicat ")) {
//                type = type.substring(8);
//            }
//            if (type.startsWith("wordnet ")) {
//                type = type.substring(type.indexOf(" ") + 1, type.lastIndexOf(" "));
//            }

            int entityId = getEntityId(entity, true), typeId = getTypeId(type, false);
            if (typeId == -1) {
                throw new RuntimeException("type not found");
            }
            entityTypeLists.get(entityId).add(typeId);
        }
        id2Entity.trimToSize();
        entityTypeLists.trimToSize();
        nEntities = id2Entity.size();
        cachedEntityTransitiveTypes = new LinkedHashSet[nEntities];

        for (int i = 0; i < nEntities; ++i) {
            entityTypeLists.get(i).trimToSize();
            // populate nEntities for types
            for (int t : getType2DistanceMapForEntity(i, Integer.MAX_VALUE).keySet()) {
                ++type2nEntities[t];
            }
        }
        // calculate itf for types
        for (int i = 0; i < nTypes; ++i) {
            // (Robertson version)
//            type2Itf[i] = Math.max(0.0001, Math.log10((nEntities - type2nEntities[i] + 0.5) / (type2nEntities[i] + 0.5)));
            // normal
            type2Itf[i] = Math.max(0.0001, Math.log(nEntities / (type2nEntities[i] + 1.0)));
        }
    }

    public HashMap<Integer, Integer> getType2DistanceMapForEntity(int entityId, int distLimit) {
        HashMap<Integer, Integer> typeId2Dist = new HashMap<>();
        if (distLimit < 1) {
            return typeId2Dist;
        }
        LinkedList<Integer> queue = new LinkedList<>();
        for (int v : entityTypeLists.get(entityId)) {
            typeId2Dist.put(v, 1);
            if (distLimit > 1) {
                queue.addLast(v);
            }
        }
        while (!queue.isEmpty()) {
            int t = queue.removeFirst();
            int tDist = typeId2Dist.get(t);
            for (int v : typeDadLists.get(t)) {
                if (typeId2Dist.containsKey(v)) {
                    continue;
                }
                typeId2Dist.put(v, tDist + 1);
                if (tDist + 1 < distLimit) {
                    queue.addLast(v);
                }
            }
        }
        return typeId2Dist;
    }

    // cached call, to get transitive types, ordered by increasing nEntities
    public LinkedHashSet<Integer> getEntityTransitiveTypesOrderedByNEntities(int entityId) {
        if (cachedEntityTransitiveTypes[entityId] != null) {
            return cachedEntityTransitiveTypes[entityId];
        }

        LinkedHashSet<Integer> types =
                getType2DistanceMapForEntity(entityId, Integer.MAX_VALUE).keySet().stream()
                        .sorted(Comparator.comparingInt(t -> type2nEntities[t]))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        return (cachedEntityTransitiveTypes[entityId] = types);
    }

    public int getEntityDistance(String entity1, String entity2) {
        Integer eId1 = entity2Id.get(entity1);
        Integer eId2 = entity2Id.get(entity2);
        if (eId1 == null || eId2 == null) {
            return -1;
        }
        if (entity1.equals(entity2)) {
            return 0;
        }
        HashMap<Integer, Integer> typeId2Dist1 = getType2DistanceMapForEntity(eId1, Integer.MAX_VALUE);
        HashMap<Integer, Integer> typeId2Dist2 = getType2DistanceMapForEntity(eId2, Integer.MAX_VALUE);
        int minDist = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Integer> e : typeId2Dist1.entrySet()) {
            Integer distToE2 = typeId2Dist2.get(e.getKey());
            if (distToE2 != null) {
                minDist = Math.min(minDist, e.getValue() + distToE2);
            }
        }
        return minDist == Integer.MAX_VALUE ? -1 : minDist;
    }

    public TaxonomyGraph() {
        this(YAGO_TAXONOMY_FILE, YAGO_TYPE_FILE);
    }
}
