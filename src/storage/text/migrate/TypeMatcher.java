package storage.text.migrate;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import nlp.NLP;
import uk.ac.susx.informatics.Morpha;
import yago.TaxonomyGraph;


public class TypeMatcher {
    private static TaxonomyGraph TAXONOMY = TaxonomyGraph.getDefaultGraphInstance();

    private String queryType, queryHeadWord;

    private Int2IntOpenHashMap matchCache;

    public TypeMatcher(String queryType) {
        this.queryType = NLP.stripSentence(NLP.fastStemming(queryType.toLowerCase(), Morpha.noun));
        this.queryHeadWord = NLP.getHeadWord(this.queryType, true);

        matchCache = new Int2IntOpenHashMap(TAXONOMY.nTypes);
        matchCache.defaultReturnValue(-1);
    }

    public boolean match(String entity) {
        Integer eId = TAXONOMY.entity2Id.get(entity);
        if (eId == null) {
            return false;
        }
        Int2IntLinkedOpenHashMap t2d = TAXONOMY.getType2DistanceMapForEntity(eId);

        for (Int2IntMap.Entry e : Int2IntMaps.fastIterable(t2d)) {
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