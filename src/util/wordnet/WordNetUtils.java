package util.wordnet;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.*;
import edu.mit.jwi.morph.IStemmer;
import edu.mit.jwi.morph.WordnetStemmer;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Deprecated
public class WordNetUtils {
    private static IDictionary dict;

    static {
        try {
            dict = new Dictionary(new File("./resources/WordNet-3.0/dict/"));
            dict.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public static Set<String> getListHypernym(ISynsetID synID, int level) {
        Set<String> hypernymsSet = new HashSet<>();
        List<ISynsetID> hypernym_tmp = dict.getSynset(synID).getRelatedSynsets(Pointer.HYPERNYM);

        Iterator iter = hypernym_tmp.iterator();
        while (iter.hasNext()) {
            ISynsetID hyperSynID = (ISynsetID) iter.next();
            /*
             * for each synset ID in the list of hypernyms send the synsetID
             * again to fetch next level hypernyms
             */
            if (level < 3) {
                getListHypernym(hyperSynID, (level + 1));
            }

            List<IWord> hypernyms = dict.getSynset(hyperSynID).getWords();
            Iterator iter2 = hypernyms.iterator();
            while (iter2.hasNext()) {
                IWord hypernym_word1 = (IWord) iter2.next();
                IWordID hypernym_word = hypernym_word1.getID();
                // System.out.println(hypernym_word);
                String[] hypParts = dict.getWord(hypernym_word).toString().split("-");
                String hyperConstruct = "";
                /*
                 * parsing the hypernym example hypernym:
                 * WID-03796768-N-01-motor_vehicle
                 */
                for (int i = 4; i < hypParts.length; i++) {
                    if (i == (hypParts.length - 1)) {
                        hyperConstruct = hyperConstruct + hypParts[i];
                    } else {
                        hyperConstruct = hyperConstruct + hypParts[i] + "-";
                    }
                }
                /*
                 * add the hyperConstruct along in a map sort the map in
                 * descending order
                 */
                hypernymsSet.add(hyperConstruct);
            }
        }
        return hypernymsSet;
    }

    /**
     * get symnonyms of the first synset
     *
     * @param noun
     * @return
     */
    public static Set<String> getSynonyms(String noun) {
        Set<String> res = new HashSet<>();
        // look up first sense of the word
        IIndexWord idxWord = dict.getIndexWord(noun, POS.NOUN);
        if (idxWord == null) {
            return res;
        }
        IWordID wordID = idxWord.getWordIDs().get(0); // 1st meaning
        ISynset synset = dict.getWord(wordID).getSynset();

        // iterate over words associated with the synset
        for (IWord w : synset.getWords()) {
            res.add(w.getLemma());
        }
        return res;
    }

    /**
     * get descriptions of synsets of a word
     */
    public static List<String> getDescriptions(String noun) {
        List<String> res = new ArrayList<>();
        // look up first sense of the word "dog "
        IIndexWord idxWord = dict.getIndexWord(noun, POS.NOUN);
        if (idxWord == null) {
            return res;
        }
        for (IWordID wid : idxWord.getWordIDs()) {
            res.add(dict.getWord(wid).getSynset().getGloss());
        }
        return res;
    }

    /**
     * get description of the first synset of a word
     */
    public static String getDescription(String noun) {
        // look up first sense of the word
        IIndexWord idxWord = dict.getIndexWord(noun, POS.NOUN);
        if (idxWord == null) {
            return null;
        }
        IWordID wordID = idxWord.getWordIDs().get(0); // 1st meaning

        IWord word = dict.getWord(wordID);
        return word.getSynset().getGloss();
    }

    /**
     * get hypernyms
     *
     * @param noun
     * @return
     */
    @Deprecated
    public static Set<String> getHypernyms(String noun) {
        Set<String> res = new HashSet<>();

        // get the synset
        IIndexWord idxWord = dict.getIndexWord(noun, POS.NOUN);
        if (idxWord == null)
            return res;
        IWordID wordID = idxWord.getWordIDs().get(0); // 1st meaning
        IWord word = dict.getWord(wordID);
        ISynset synset = word.getSynset();

        // get the hypernyms
        List<ISynsetID> hypernyms = synset.getRelatedSynsets(Pointer.HYPERNYM);
        // print out each h y p e r n y m s id and synonyms
        List<IWord> words;
        for (ISynsetID sid : hypernyms) {
            words = dict.getSynset(sid).getWords();

            for (Iterator<IWord> i = words.iterator(); i.hasNext(); ) {
                res.add(i.next().getLemma());
            }
        }
        return res;
    }

    /**
     * get inherited hypernym of a given synset ID
     * return a linked list of synset IDs
     *
     * @param synsetID
     * @return
     */
    @Deprecated
    public static LinkedList<ISynsetID> getInheritedHypernym(ISynsetID synsetID) {
        LinkedList<ISynsetID> res = new LinkedList<>();

        while (dict.getSynset(synsetID).getRelatedSynsets(Pointer.HYPERNYM).size() > 0) {
            List<ISynsetID> hypernym_tmp = dict.getSynset(synsetID).getRelatedSynsets(Pointer.HYPERNYM);
            //IWord word = dict.getSynset(hypernym_tmp.get(0)).getWord(1);
            res.add(hypernym_tmp.get(0));
            synsetID = hypernym_tmp.get(0);
        }
        return res;
    }

    /**
     * get inherited hypernym of a given synset ID
     * return a linked list of words (get from synset IDs)
     *
     * @param synsetID
     * @return
     */
    @Deprecated
    public static LinkedList<String> getInheritedHypernymWord(ISynsetID synsetID) {

        LinkedList<String> res = new LinkedList<>();

        LinkedList<ISynsetID> synIDs = getInheritedHypernym(synsetID);
        for (ISynsetID sid : synIDs) {
            IWord word = dict.getSynset(sid).getWord(1);
            //replace "abstraction" to "abstract_entity"
            String lemma = word.getLemma();
            if (lemma.equals("abstraction")) lemma = "abstract_entity";
            res.add("wn_" + lemma);
        }
        return res;
    }

    @Deprecated
    public static String getLemma(ISynsetID sid) {

        IWord word = dict.getSynset(sid).getWord(1);
        //replace "abstraction" to "abstract_entity"
        String lemma = word.getLemma();
        if (lemma.equals("abstraction")) lemma = "abstract_entity";
        return lemma;
    }

    /**
     * get inherited hypernym of a given noun, get the first meaning of this word
     * return a list of words (get from synset IDs)
     *
     * @param noun
     * @return
     */
    @Deprecated
    public static LinkedList<String> getInheritedHypernymWord(String noun) {


        LinkedList<String> res = new LinkedList<>();
        // get the synset
        IIndexWord idxWord = dict.getIndexWord(noun, POS.NOUN);
        if (idxWord == null)
            return res;
        IWordID wordID = idxWord.getWordIDs().get(0); // 1st meaning
        IWord word = dict.getWord(wordID);
        ISynset synset = word.getSynset();

        res = getInheritedHypernymWord(synset.getID());

        return res;
    }

    @Deprecated
    public static String inheritedHypernymWordToString(String noun) {
        LinkedList<String> res = getInheritedHypernymWord(noun);
        if (res.size() == 0) return "NO ROOT PATH";
        String tmp = "";
        for (String s : res) {
            tmp += s + " <= ";
        }
        return tmp.substring(0, tmp.lastIndexOf("<") - 1);
    }


    /**
     * get hypernyms of a list of nouns
     *
     * @param nouns
     * @return
     */
    @Deprecated
    public static Set<String> getHypernyms(Set<String> nouns) {
        Set<String> res = new HashSet<>();

        for (String n : nouns)
            res.addAll(getHypernyms(n));
        res.addAll(nouns);
        return res;
    }

    /**
     * get hypernyms in multilevel of a given noun
     *
     * @param noun
     * @param iter  = 1
     * @param level : number of levels (e.g. 3, 4)
     * @return
     */
    @Deprecated
    public static Set<String> getMultilevelHypernyms(String noun, int iter, int level) {
        if (iter == level)
            return getHypernyms(noun);
        else
            return getHypernyms(getMultilevelHypernyms(noun, ++iter, level));
    }

    public static void main(String[] args) {
        IStemmer stemmer = new WordnetStemmer(dict);
        System.out.println(stemmer.findStems("thought", null));

        IIndexWord idxWord = dict.getIndexWord("believe", POS.VERB);
        IWordID wordID = idxWord.getWordIDs().get(0);
        IWord word = dict.getWord(wordID);
        System.out.println("Id = " + wordID);
        System.out.println("Lemma = " + word.getLemma());
        System.out.println("Gloss = " + word.getSynset().getGloss());

        idxWord = dict.getIndexWord("think", POS.VERB);
        wordID = idxWord.getWordIDs().get(0);
        word = dict.getWord(wordID);
        System.out.println("Id = " + wordID);
        System.out.println("Lemma = " + word.getLemma());
        System.out.println("Gloss = " + word.getSynset().getGloss());

//        System.out.println(getMultilevelHypernyms("villain", 1, 1));
//        System.out.println(getMultilevelHypernyms("child", 1, 1));
//        System.out.println(getMultilevelHypernyms("dog", 1, 1));
//        System.out.println(getMultilevelHypernyms("player", 1, 1));
//
//        // System.out.println(getHypernyms("male_person", 2, 0));
//
//        List<String> descs = getDescriptions("villain");
//        for (String s : descs) {
//            System.out.println(s);
//        }
//        System.out.println(getHypernyms("dragon"));
//        System.out.println(inheritedHypernymWordToString("organism"));
    }
}