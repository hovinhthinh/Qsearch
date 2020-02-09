package yago;

import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import util.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Logger;

public class TaxonomyGraph {
    public static final Logger LOGGER = Logger.getLogger(TaxonomyGraph.class.getName());
    public static final String YAGO_TAXONOMY_FILE = "/GW/D5data-11/hvthinh/yago-taxonomy/yagoTaxonomy.tsv.gz";
    public static final String YAGO_TYPE_FILE = "/GW/D5data-11/hvthinh/yago-taxonomy/yagoTypes.tsv.gz";

    public HashMap<String, Integer> type2Id;
    public ArrayList<String> id2Type;
    public int[] type2nEntities;
    public double[] type2Itf;
    public ArrayList<IntArrayList> typeDadLists;
    public ArrayList<IntArrayList> typeChildLists;
    public int[] type2DistanceToRoot; // always >= 1

    public HashMap<String, Integer> entity2Id;
    public ArrayList<String> id2Entity;
    public ArrayList<IntArrayList> entityTypeLists;
    public int nEntities;
    public int nTypes;

    private static transient final int CACHE_ENTITY_TRANSITIVE_TYPE_2_DISTANCE_SIZE = 100000;
    private transient Int2ObjectLinkedOpenHashMap<Int2IntLinkedOpenHashMap> cachedEntityTransitiveType2Distance;

    private static transient final int CACHE_MOST_SPEC_COMMON_TYPE_SIZE = 1000000;
    private transient Long2IntLinkedOpenHashMap cachedEntityMostSpecificCommonType;

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
        typeDadLists.add(new IntArrayList());
        typeChildLists.add(new IntArrayList());
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
        entityTypeLists.add(new IntArrayList());
        return id2Entity.size() - 1;
    }

    public TaxonomyGraph(String yagoTaxonomyFile, String yagoTypeFile) {
        LOGGER.info("Loading YAGO taxonomy graph");
        // Load taxonomy
        type2Id = new HashMap<>();
        id2Type = new ArrayList<>();
        typeDadLists = new ArrayList<>();
        typeChildLists = new ArrayList<>();
        for (String line : FileUtils.getLineStream(yagoTaxonomyFile, "UTF-8")) {
            String[] arr = line.split("\t");
            if (arr.length != 4 || !arr[2].equals("rdfs:subClassOf")) {
                continue;
            }
            String childType = arr[1], parentType = arr[3];
            int childId = getTypeId(childType, true), dadId = getTypeId(parentType, true);
            typeDadLists.get(childId).add(dadId);
            typeChildLists.get(dadId).add(childId);
        }
        id2Type.trimToSize();
        typeDadLists.trimToSize();
        for (IntArrayList l : typeDadLists) {
            l.trim();
        }
        typeChildLists.trimToSize();
        for (IntArrayList l : typeChildLists) {
            l.trim();
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
        cachedEntityTransitiveType2Distance = new Int2ObjectLinkedOpenHashMap<>();

        for (int i = 0; i < nEntities; ++i) {
            entityTypeLists.get(i).trim();
            // populate nEntities for types
            for (Int2IntMap.Entry e : Int2IntMaps.fastIterable(getType2DistanceMapForEntity(i))) {
                ++type2nEntities[e.getIntKey()];
            }
        }
        // calculate itf for types
        for (int i = 0; i < nTypes; ++i) {
            // (Robertson version)
//            type2Itf[i] = Math.max(0.0001, Math.log10((nEntities - type2nEntities[i] + 0.5) / (type2nEntities[i] + 0.5)));
            // normal
            type2Itf[i] = Math.max(0.0001, Math.log(nEntities / (type2nEntities[i] + 1.0)));
        }

        // compute type 2 distance to root
        type2DistanceToRoot = new int[nTypes];
        LinkedList<Integer> queue = new LinkedList<>();
        for (int i = 0; i < nTypes; ++i) {
            if (typeDadLists.get(i).size() == 0) {
                type2DistanceToRoot[i] = 1;
                queue.addLast(i);
            }
        }
        while (!queue.isEmpty()) {
            int t = queue.removeFirst();
            for (int v : typeChildLists.get(t)) {
                if (type2DistanceToRoot[v] == 0) {
                    type2DistanceToRoot[v] = type2DistanceToRoot[t] + 1;
                    queue.addLast(v);
                }
            }
        }

        // common type cache
        cachedEntityMostSpecificCommonType = new Long2IntLinkedOpenHashMap();
        cachedEntityMostSpecificCommonType.defaultReturnValue(-2);
    }

    // ordered by increasing distance
    public Int2IntLinkedOpenHashMap getType2DistanceMapForEntity(int entityId) {
        Int2IntLinkedOpenHashMap typeId2Dist = cachedEntityTransitiveType2Distance.getAndMoveToFirst(entityId);
        if (typeId2Dist != null) {
            return typeId2Dist;
        }

        typeId2Dist = new Int2IntLinkedOpenHashMap();
        typeId2Dist.defaultReturnValue(-1);
        LinkedList<Integer> queue = new LinkedList<>();
        for (int v : entityTypeLists.get(entityId)) {
            typeId2Dist.put(v, 1);
            queue.addLast(v);
        }
        while (!queue.isEmpty()) {
            int t = queue.removeFirst();
            int tDist = typeId2Dist.get(t);
            for (int v : typeDadLists.get(t)) {
                if (typeId2Dist.containsKey(v)) {
                    continue;
                }
                typeId2Dist.put(v, tDist + 1);
                queue.addLast(v);
            }
        }
        cachedEntityTransitiveType2Distance.putAndMoveToFirst(entityId, typeId2Dist);
        if (cachedEntityTransitiveType2Distance.size() > CACHE_ENTITY_TRANSITIVE_TYPE_2_DISTANCE_SIZE) {
            cachedEntityTransitiveType2Distance.removeLast();
        }
        return typeId2Dist;
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
        Int2IntLinkedOpenHashMap typeId2Dist1 = getType2DistanceMapForEntity(eId1);
        Int2IntLinkedOpenHashMap typeId2Dist2 = getType2DistanceMapForEntity(eId2);
        int minDist = Integer.MAX_VALUE;
        for (Int2IntMap.Entry t : Int2IntMaps.fastIterable(typeId2Dist1)) {
            if (t.getIntValue() >= minDist) {
                break;
            }
            int distToE2 = typeId2Dist2.get(t.getIntKey());
            if (distToE2 != -1) {
                minDist = Math.min(minDist, t.getIntValue() + distToE2);
            }
        }
        return minDist == Integer.MAX_VALUE ? -1 : minDist;
    }

    // return -1 if cannot find.
    public int getMostSpecificCommonType(int entityId1, int entityId2) {
        if (entityId1 > entityId2) {
            int tmp = entityId1;
            entityId1 = entityId2;
            entityId2 = tmp;
        }
        long key = 1000000000L * entityId1 + entityId2;
        int result = cachedEntityMostSpecificCommonType.getAndMoveToFirst(key);
        if (result != -2) {
            return result;
        }

        Int2IntLinkedOpenHashMap typeId2Dist1 = getType2DistanceMapForEntity(entityId1);
        Int2IntLinkedOpenHashMap typeId2Dist2 = getType2DistanceMapForEntity(entityId2);
        result = -1;
        for (Int2IntMap.Entry t : Int2IntMaps.fastIterable(typeId2Dist1)) {
            if (!typeId2Dist2.containsKey(t.getIntKey())) {
                continue;
            }
            if (result == -1 || type2nEntities[t.getIntKey()] < type2nEntities[result]) {
                result = t.getIntKey();
            }
        }
        cachedEntityMostSpecificCommonType.putAndMoveToFirst(key, result);
        if (cachedEntityMostSpecificCommonType.size() > CACHE_MOST_SPEC_COMMON_TYPE_SIZE) {
            cachedEntityMostSpecificCommonType.removeLastInt();
        }
        return result;
    }

    public TaxonomyGraph() {
        this(YAGO_TAXONOMY_FILE, YAGO_TYPE_FILE);
    }
}
