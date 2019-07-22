package model.context;

import edu.knowitall.openie.OpenIE;
import edu.knowitall.tool.postag.PostaggedToken;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.*;
import nlp.NLP;
import nlp.Static;
import scala.collection.JavaConversions;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.headword.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class KullBackLeiblerMatcher implements ContextMatcher {
    private static final double MIN_BLM_COUNT = 0.0001;
    private static IDictionary WN_DICT;
    private static HashMap<String, Integer> BLM;

    static {
        try {
            WN_DICT = new Dictionary(new File("./resources/WordNet-3.0/dict/"));
            WN_DICT.open();

            BLM = new HashMap<>();
            for (String line : FileUtils.getLineStream("./data/blm_stics+nyt/all.gz", "UTF-8")) {
                int pos = line.indexOf("\t");
                BLM.put(line.substring(0, pos), Integer.parseInt(line.substring(pos + 1)));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private double smoothingWeight;

    public KullBackLeiblerMatcher(double smoothingWeight) {
        this.smoothingWeight = smoothingWeight;
    }

    public static HashMap<String, Double> getDistributedProbability(HashSet<String> words) {
        double total = 0;
        for (String w : words) {
            int count = BLM.getOrDefault(w, 0);
            total += count != 0 ? count : MIN_BLM_COUNT;
        }
        HashMap<String, Double> result = new HashMap<>();
        for (String w : words) {
            int count = BLM.getOrDefault(w, 0);
            result.put(w, total == 0 ? 0 : (count != 0 ? count : MIN_BLM_COUNT) / total);
        }
        return result;
    }

    // expands with the first synset of the right postag.
    public static ArrayList<String> wordnetBasedContextExpanding(List<String> context) {
        List<PostaggedToken> tokens = null;
        OpenIE openIE = Static.getOpenIe();
        synchronized (openIE) {
            tokens = JavaConversions.seqAsJavaList(openIE.postagger().postag(NLP.join(" ", context)));
        }
        StringBuilder result = new StringBuilder();
        for (PostaggedToken token : tokens) {
            POS pos = null;
            if (token.postag().startsWith("NN")) {
                pos = POS.NOUN;
            } else if (token.postag().startsWith("JJ")) {
                pos = POS.ADJECTIVE;
            } else if (token.postag().startsWith("VB")) {
                pos = POS.VERB;
            } else if (token.postag().startsWith("RB")) {
                pos = POS.ADVERB;
            }
            if (pos == null) {
                result.append(" ").append(token.string());
                continue;
            }
            try {
                IIndexWord idxWord = WN_DICT.getIndexWord(token.string(), pos);
                IWordID wordID = idxWord.getWordIDs().get(0); // 1st meaning
                ISynset synset = WN_DICT.getWord(wordID).getSynset();
                for (IWord w : synset.getWords()) {
                    result.append(" ").append(w.getLemma());
                }
            } catch (Exception e) {
                result.append(" ").append(token.string());
            }
        }

        return NLP.splitSentence(NLP.replaceNonLetterOrDigitBySpace(result.toString()));
    }

    // contexts should be stemmed and in lowercase. // we assume terms in contexts are different (appear only 1).
    // Jelinek-Mercer smoothing for P(w|factContext): (1 - lambda) * P(LM) + lambda * P(BLM)
    @Override
    public double match(ArrayList<String> queryContext, ArrayList<String> factContext) {
        // Expand query.
        queryContext = wordnetBasedContextExpanding(queryContext);
        for (int j = 0; j < queryContext.size(); ++j) {
            queryContext.set(j, StringUtils.stem(queryContext.get(j).toLowerCase(), Morpha.any));
        }

        HashSet<String> vocab = new HashSet<>();
        vocab.addAll(queryContext);
        vocab.addAll(factContext);
        HashMap<String, Double> probDist = getDistributedProbability(vocab);

        HashSet<String> factTermSet = new HashSet<>(factContext);

        double result = 0;

        for (String t : queryContext) {
            result -= Math.log((1 - smoothingWeight) * (factTermSet.contains(t) ? (1.0 / factContext.size()) : 0.0)
                    + smoothingWeight * probDist.get(t));
        }

        return result / queryContext.size();
    }
}
