package eval.baseline;

import model.context.IDF;
import nlp.Glove;
import nlp.NLP;
import util.Vectors;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Deprecated
public class WordSet {
    public HashMap<String, Integer> word2freq = new HashMap<>();

    public void allAll(Collection<String> words) {
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

    public double[] getTfIdfEmbedding() {
        double[] r = new double[Glove.DIM];
        double sumTfIdf = 0;
        for (Map.Entry<String, Integer> e : word2freq.entrySet()) {
            double[] emb = Glove.getEmbedding(e.getKey());
            if (e == null) {
                continue;
            }
            double tfIdf = IDF.getDefaultIdf(e.getKey()) * e.getValue();
            sumTfIdf += tfIdf;
            r = Vectors.sum(r, Vectors.multiply(emb, tfIdf));
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
