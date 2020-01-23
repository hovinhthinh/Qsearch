package eval.baseline;

import model.context.IDF;
import nlp.Glove;
import nlp.NLP;
import uk.ac.susx.informatics.Morpha;
import util.Vectors;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Deprecated
public class WordSet {
    public HashMap<String, Integer> word2freq = new HashMap<>();

    public void addAll(Collection<String> words) {
        for (String w : words) {
            addSingle(w);
        }
    }

    public void addSingle(String w) {
        w = w.toLowerCase();
        word2freq.put(w, word2freq.getOrDefault(w, 0) + 1);
    }

    public void addAll(String s) {
        for (String w : s.split(" ")) {
            addSingle(w);
        }
    }

    public void removeStopwords() {
        for (String w : NLP.BLOCKED_STOPWORDS) {
            word2freq.remove(w);
        }
    }

    public void stemming() {
        HashMap<String, Integer> newWord2freq = new HashMap<>();
        for (Map.Entry<String, Integer> e : word2freq.entrySet()) {
            String k = NLP.fastStemming(e.getKey(), Morpha.any);
            newWord2freq.put(k, newWord2freq.getOrDefault(k, 0) + e.getValue());
        }
        word2freq = newWord2freq;
    }

    public double[] getTfIdfWeightedEmbedding() {
        double[] r = new double[Glove.DIM];
        double sumTfIdf = 0;
        for (Map.Entry<String, Integer> e : word2freq.entrySet()) {
            double[] emb = Glove.getEmbedding(e.getKey());
            if (emb == null) {
                continue;
            }
            double tfIdf = IDF.getDefaultIdf(e.getKey(), false) * e.getValue();
            sumTfIdf += tfIdf;
            r = Vectors.sum(r, Vectors.multiply(emb, tfIdf));
        }
        r = Vectors.multiply(r, 1 / sumTfIdf);
        return r;
    }

    public double[] getIdfWeightedEmbedding() {
        double[] r = new double[Glove.DIM];
        double sumTfIdf = 0;
        for (Map.Entry<String, Integer> e : word2freq.entrySet()) {
            double[] emb = Glove.getEmbedding(e.getKey());
            if (emb == null) {
                continue;
            }
            double idf = IDF.getDefaultIdf(e.getKey(), false);
            sumTfIdf += idf;
            r = Vectors.sum(r, Vectors.multiply(emb, idf));
        }
        r = Vectors.multiply(r, 1 / sumTfIdf);
        return r;
    }

    public double getJaccardSim(WordSet other) {
        int sum = word2freq.size() + other.word2freq.size();
        int overlap = 0;
        for (String k : word2freq.keySet()) {
            if (other.word2freq.containsKey(k)) {
                ++overlap;
                --sum;
            }
        }
        return ((double) overlap) / sum;
    }
}
