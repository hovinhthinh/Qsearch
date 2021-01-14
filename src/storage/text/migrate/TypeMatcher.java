package storage.text.migrate;

import it.unimi.dsi.fastutil.ints.*;
import nlp.NLP;
import uk.ac.susx.informatics.Morpha;
import yago.TaxonomyGraph;


public class TypeMatcher {
    private static TaxonomyGraph TAXONOMY = TaxonomyGraph.getDefaultGraphInstance();

    private String queryType, queryHeadWord;

    private IntOpenHashSet possibleValidEntities;

    private Int2IntOpenHashMap matchCache;

    public TypeMatcher(String queryType) {
        this.queryType = NLP.stripSentence(NLP.fastStemming(queryType.toLowerCase(), Morpha.noun));
        this.queryHeadWord = NLP.getHeadWord(this.queryType, true);
        this.possibleValidEntities = TAXONOMY.typeHeadWord2EntityIds.get(queryHeadWord);

        matchCache = new Int2IntOpenHashMap(TAXONOMY.nTypes);
        matchCache.defaultReturnValue(-1);
    }

    public Int2IntLinkedOpenHashMap type2DistanceMapForLastCheckedValidEntity;

    public boolean match(String entity) {
        Integer eId = TAXONOMY.entity2Id.get(entity);
        if (eId == null || possibleValidEntities == null || !possibleValidEntities.contains((int) eId)) {
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