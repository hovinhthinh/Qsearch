package yago;

import config.Configuration;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import nlp.NLP;
import org.apache.commons.lang.StringEscapeUtils;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.Pair;

import java.util.*;
import java.util.logging.Logger;

public class TaxonomyGraph {
    public static final Logger LOGGER = Logger.getLogger(TaxonomyGraph.class.getName());
    public static final String YAGO_TAXONOMY_FILE = Configuration.get("yago.file_path.taxonomy");
    public static final String YAGO_TYPE_FILE = Configuration.get("yago.file_path.type");

    public HashMap<String, Integer> type2Id;
    public ArrayList<String> id2Type;
    public ArrayList<String> id2TextualizedType;
    public ArrayList<String> id2TextualizedTypeHeadWord;
    public HashMap<String, IntOpenHashSet> typeHeadWord2EntityIds;

    public int[] type2nEntities;
    public double[] type2Itf;
    public ArrayList<IntArrayList> typeDadLists;
    public ArrayList<IntArrayList> typeChildLists;
    public int[] type2DistanceToRoot; // always >= 1

    public HashMap<String, Integer> entity2Id;
    public ArrayList<String> id2Entity;
    public ArrayList<IntArrayList> entityTypeLists;
    public ArrayList<IntArrayList> typeEntityLists;
    public int nEntities;
    public int nTypes;

    private static transient final int CACHE_ENTITY_TRANSITIVE_TYPE_2_DISTANCE_SIZE = 100000;
    private transient Int2ObjectLinkedOpenHashMap<Int2IntLinkedOpenHashMap> cachedEntityTransitiveType2Distance;

    private static transient final int CACHE_MOST_SPEC_COMMON_TYPE_SIZE = 1000000;
    private transient Long2DoubleLinkedOpenHashMap cachedEntityTypeAgreement;

    private static TaxonomyGraph DEFAULT_GRAPH;

    public static TaxonomyGraph getDefaultGraphInstance() {
        if (DEFAULT_GRAPH == null) {
            DEFAULT_GRAPH = new TaxonomyGraph();
        }
        return DEFAULT_GRAPH;
    }

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
        typeEntityLists.add(new IntArrayList());
        return id2Type.size() - 1;
    }

    public int getTypeId(String type) {
        return getTypeId(type, false);
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

    public int getEntityId(String entity) {
        return getEntityId(entity, false);
    }

    public static String textualize(String type) {
        type = NLP.fastStemming(NLP.stripSentence(type.replaceAll("[^A-Za-z0-9]", " ")).toLowerCase(), Morpha.noun);
        if (type.startsWith("wikicat ")) {
            type = type.substring(8);
        }
        if (type.startsWith("wordnet ")) {
            type = type.substring(type.indexOf(" ") + 1, type.lastIndexOf(" "));
        }
        return type;
    }

    public TaxonomyGraph(String yagoTaxonomyFile, String yagoTypeFile) {
        LOGGER.info("Loading YAGO taxonomy graph");
        // Load taxonomy
        type2Id = new HashMap<>();
        id2Type = new ArrayList<>();
        typeDadLists = new ArrayList<>();
        typeChildLists = new ArrayList<>();
        typeEntityLists = new ArrayList<>();
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

        // Textualized types
        id2TextualizedType = new ArrayList<>(nTypes);
        id2TextualizedTypeHeadWord = new ArrayList<>(nTypes);
        for (String t : id2Type) {
            String textualized = textualize(t);
            id2TextualizedType.add(textualized);
            id2TextualizedTypeHeadWord.add(NLP.getHeadWord(textualized, true));
        }
        id2TextualizedType.trimToSize();
        id2TextualizedTypeHeadWord.trimToSize();

        // Load entity types
        entity2Id = new HashMap<>();
        id2Entity = new ArrayList<>();
        entityTypeLists = new ArrayList<>();
        for (String line : FileUtils.getLineStream(yagoTypeFile, "UTF-8")) {
            String[] arr = line.split("\t");
            if (arr.length != 4 || !arr[2].equals("rdf:type")) {
                continue;
            }
            String entity = StringEscapeUtils.unescapeJava(arr[1]), type = arr[3];

            int entityId = getEntityId(entity, true), typeId = getTypeId(type, false);
            if (typeId == -1) {
                throw new RuntimeException("type not found");
            }
            entityTypeLists.get(entityId).add(typeId);
            typeEntityLists.get(typeId).add(entityId);
        }
        id2Entity.trimToSize();
        entityTypeLists.trimToSize();
        typeEntityLists.trimToSize();
        nEntities = id2Entity.size();
        cachedEntityTransitiveType2Distance = new Int2ObjectLinkedOpenHashMap<>();

        for (int i = 0; i < nEntities; ++i) {
            entityTypeLists.get(i).trim();
            // populate nEntities for types
            for (Int2IntMap.Entry e : Int2IntMaps.fastIterable(getType2DistanceMapForEntity(i))) {
                ++type2nEntities[e.getIntKey()];
            }
        }
        for (int i = 0; i < nTypes; ++i) {
            typeEntityLists.get(i).trim();
            // calculate itf for types
            // (Robertson version)
            type2Itf[i] = Math.max(0.0001, Math.log10((nEntities - type2nEntities[i] + 0.5) / (type2nEntities[i] + 0.5)));
            // normal
//            type2Itf[i] = Math.max(0.0001, Math.log(nEntities / (type2nEntities[i] + 1.0)));
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
        cachedEntityTypeAgreement = new Long2DoubleLinkedOpenHashMap();
        cachedEntityTypeAgreement.defaultReturnValue(-1);

        // typeHeadWord2EntityIds
        typeHeadWord2EntityIds = new HashMap<>();
        HashSet<String> typeHeadWordSet = new HashSet<>();
        for (int i = 0; i < nEntities; ++i) {
            typeHeadWordSet.clear();
            for (Int2IntMap.Entry t : Int2IntMaps.fastIterable(getType2DistanceMapForEntity(i))) {
                typeHeadWordSet.add(id2TextualizedTypeHeadWord.get(t.getIntKey()));
            }
            for (String hw : typeHeadWordSet) {
                typeHeadWord2EntityIds.putIfAbsent(hw, new IntOpenHashSet());
                typeHeadWord2EntityIds.get(hw).add(i);
            }
        }
        for (Map.Entry<String, IntOpenHashSet> e : typeHeadWord2EntityIds.entrySet()) {
            e.getValue().trim();
        }
    }

    // ordered by increasing distance
    public Int2IntLinkedOpenHashMap getType2DistanceMapForEntityWithCache(int entityId) {
        Int2IntLinkedOpenHashMap typeId2Dist = cachedEntityTransitiveType2Distance.getAndMoveToFirst(entityId);
        if (typeId2Dist != null) {
            return typeId2Dist;
        }

        typeId2Dist = getType2DistanceMapForEntity(entityId);

        cachedEntityTransitiveType2Distance.putAndMoveToFirst(entityId, typeId2Dist);
        if (cachedEntityTransitiveType2Distance.size() > CACHE_ENTITY_TRANSITIVE_TYPE_2_DISTANCE_SIZE) {
            cachedEntityTransitiveType2Distance.removeLast();
        }
        return typeId2Dist;
    }

    public Int2IntLinkedOpenHashMap getType2DistanceMapForEntity(int entityId) {
        Int2IntLinkedOpenHashMap typeId2Dist = new Int2IntLinkedOpenHashMap();
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
//        typeId2Dist.trim();
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
        Int2IntLinkedOpenHashMap typeId2Dist1 = getType2DistanceMapForEntityWithCache(eId1);
        Int2IntLinkedOpenHashMap typeId2Dist2 = getType2DistanceMapForEntityWithCache(eId2);
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

    public double getTypeAgreement(int entityId1, int entityId2) {
        if (entityId1 > entityId2) {
            int tmp = entityId1;
            entityId1 = entityId2;
            entityId2 = tmp;
        }
        long key = 1000000000L * entityId1 + entityId2;
        double result = cachedEntityTypeAgreement.getAndMoveToFirst(key);
        if (result != -1) {
            return result;
        }

        Int2IntLinkedOpenHashMap typeId2Dist1 = getType2DistanceMapForEntityWithCache(entityId1);
        Int2IntLinkedOpenHashMap typeId2Dist2 = getType2DistanceMapForEntityWithCache(entityId2);

        int lca = -1;
        for (Int2IntMap.Entry t : Int2IntMaps.fastIterable(typeId2Dist1)) {
            if (!typeId2Dist2.containsKey(t.getIntKey())) {
                continue;
            }
            if (lca == -1 || type2nEntities[t.getIntKey()] < type2nEntities[lca]) {
                lca = t.getIntKey();
            }
        }

        result = (lca == -1 ? 0 : type2Itf[lca]);

        cachedEntityTypeAgreement.putAndMoveToFirst(key, result);
        if (cachedEntityTypeAgreement.size() > CACHE_MOST_SPEC_COMMON_TYPE_SIZE) {
            cachedEntityTypeAgreement.removeLastDouble();
        }
        return result;
    }

    public HashSet<Integer> getEntitySetForTypes(Collection<Integer> typeIds) {
        HashSet<Integer> entitySet = new HashSet<>();

        LinkedList<Integer> queue = new LinkedList<>();
        IntOpenHashSet visitedTypeSet = new IntOpenHashSet();
        queue.addAll(typeIds);
        visitedTypeSet.addAll(typeIds);
        while (!queue.isEmpty()) {
            int t = queue.removeFirst();
            entitySet.addAll(typeEntityLists.get(t));
            IntArrayList subTypes = typeChildLists.get(t);
            for (int c : subTypes) {
                if (visitedTypeSet.add(c)) {
                    queue.addLast(c);
                }
            }
        }
        return entitySet;
    }

    // These types have more than 1M entities in YAGO, however some of them are good, so being excluded from the list.
    private static final HashSet<String> BLOCKED_GENERAL_TEXTUALIZED_TYPES = new HashSet<>(Arrays.asList(
            "owl thing",
            "physical entity",
            "object",
            "whole",
            "yagolegalactorgeo",
            "yagolegalactor",
            "yagopermanentlylocatedentity",
//            "living thing",
//            "organism",
            "causal agent",
//            "person",
            "person associated with building and structure",
            "person associated with place",
            "abstraction",
            "yagogeoentity",
//            "artifact",
//            "european person",
            "physical object"
    ));

    public Set<String> getTextualizedTypes(String entity, boolean specificOnly) { // entity: <Cris_Ronaldo>
        Integer eId = entity2Id.get(entity);
        if (eId == null) {
            return null;
        }
        Int2IntLinkedOpenHashMap t2d = getType2DistanceMapForEntity(eId);

        Set<String> types = new HashSet<>(t2d.size());
        for (Int2IntMap.Entry e : Int2IntMaps.fastIterable(t2d)) {
            String t = id2TextualizedType.get(e.getIntKey());
            if (!specificOnly || !BLOCKED_GENERAL_TEXTUALIZED_TYPES.contains(t)) {
                types.add(t);
            }
        }
        return types;
    }

    public TaxonomyGraph() {
        this(YAGO_TAXONOMY_FILE, YAGO_TYPE_FILE);
    }

    public void printTypeHierarchyForEntity(String entity) {
        System.out.println(entity);

        Integer eId = entity2Id.get(entity);
        if (eId == null) {
            return;
        }
        HashSet<Integer> visitedTypeIds = new HashSet<>();
        Stack<Pair<Integer, Integer>> typeId2Dist = new Stack<>();
        for (int v : entityTypeLists.get(eId)) {
            typeId2Dist.push(new Pair(v, 1));
            visitedTypeIds.add(v);
        }
        while (!typeId2Dist.isEmpty()) {
            Pair<Integer, Integer> t = typeId2Dist.pop();
            for (int i = 0; i < t.second - 1; ++i) {
                System.out.print("    ");
            }
            System.out.print("----");
            System.out.println(id2Type.get(t.first));
            for (int v : typeDadLists.get(t.first)) {
                if (!visitedTypeIds.contains(v)) {
                    typeId2Dist.push(new Pair<>(v, t.second + 1));
                    visitedTypeIds.add(v);
                }
            }
        }
    }

    public static void main(String[] args) {
        long time = System.currentTimeMillis();
        getDefaultGraphInstance().printTypeHierarchyForEntity("<China_Construction_Bank>");
        System.out.println(System.currentTimeMillis() - time);
    }
}
