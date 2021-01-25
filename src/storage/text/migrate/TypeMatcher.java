package storage.text.migrate;

import it.unimi.dsi.fastutil.ints.*;
import nlp.NLP;
import uk.ac.susx.informatics.Morpha;
import yago.TaxonomyGraph;

import java.util.LinkedList;

public class TypeMatcher {
    private static TaxonomyGraph TAXONOMY = TaxonomyGraph.getDefaultGraphInstance();

    // For raw query type
    private String queryType, queryHeadWord;
    private IntOpenHashSet possibleValidEntities;
    private Int2IntOpenHashMap matchCache;

    // For query type in the form of yago ids
    private IntOpenHashSet validDirectTypeIds;
    public Integer queryYagoTypeId;

    public TypeMatcher(String queryType) {
        // check if it is a string of a type in yago.
        Integer yagoTypeId = TAXONOMY.type2Id.get(queryType);
        if (yagoTypeId != null) {
            initWithYagoTypeId(yagoTypeId);
            return;
        }

        // otherwise it is a raw type
        this.queryType = NLP.stripSentence(NLP.fastStemming(queryType.toLowerCase(), Morpha.noun));
        this.queryHeadWord = NLP.getHeadWord(this.queryType, true);
        this.possibleValidEntities = TAXONOMY.typeHeadWord2EntityIds.get(queryHeadWord);

        matchCache = new Int2IntOpenHashMap(TAXONOMY.nTypes);
        matchCache.defaultReturnValue(-1);
    }

    public TypeMatcher(int yagoTypeId) {
        initWithYagoTypeId(yagoTypeId);
    }

    private void initWithYagoTypeId(int yagoTypeId) {
        queryYagoTypeId = yagoTypeId;
        validDirectTypeIds = new IntOpenHashSet();
        LinkedList<Integer> queue = new LinkedList<>();
        validDirectTypeIds.add(yagoTypeId);
        queue.addLast(yagoTypeId);
        while (!queue.isEmpty()) {
            int t = queue.removeFirst();
            for (int v : TAXONOMY.typeChildLists.get(t)) {
                if (validDirectTypeIds.contains(v)) {
                    continue;
                }
                validDirectTypeIds.add(v);
                queue.addLast(v);
            }
        }
    }

    public Int2IntLinkedOpenHashMap type2DistanceMapForLastCheckedValidEntity;

    public boolean match(String entity) {
        Integer eId = TAXONOMY.entity2Id.get(entity);
        if (eId == null) {
            return false;
        }
        // query type in the form of yago ids
        if (validDirectTypeIds != null) {
            for (int v : TAXONOMY.entityTypeLists.get(eId)) {
                if (validDirectTypeIds.contains(v)) {
                    return true;
                }
            }
            return false;
        }

        // raw query type
        if (possibleValidEntities == null || !possibleValidEntities.contains((int) eId)) {
            return false;
        }
        type2DistanceMapForLastCheckedValidEntity = TAXONOMY.getType2DistanceMapForEntity(eId);

        for (Int2IntMap.Entry e : Int2IntMaps.fastIterable(type2DistanceMapForLastCheckedValidEntity)) {
            int valid = matchCache.get(e.getIntKey());
            if (valid == -1) {
                String type = TAXONOMY.id2TextualizedType.get(e.getIntKey());
                valid = (type.contains(queryType) && queryHeadWord.equals(NLP.getHeadWord(type, true))) ? 1 : 0;
                matchCache.put(e.getIntKey(), valid);
            }
            if (valid == 1) {
                return true;
            }
        }
        return false;
    }
}