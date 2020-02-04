package nlp;

import org.apache.commons.collections4.map.LRUMap;
import util.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;

// Return a non-negative value. A value of 0 means that the 2 strings are the same.
// A value of -1 means that similarity cannot be computed (embedding is unavailable).
public class Glove {
    public static final String DATA_DIR = "./resources/glove/";
    public static final int DIM = 100; // 50, 100, 200 or 300.

    private static HashMap<String, Integer> EMBEDDING_ID = null;
    private static ArrayList<double[]> EMBEDDING_VALUE = null;

    private static LRUMap<Long, Double> CACHE_COSINE = new LRUMap<>(10000000);

    private synchronized static void init() {
        if (EMBEDDING_ID != null) {
            return;
        }
        String embeddingFile = DATA_DIR + "glove.6B." + DIM + "d.txt";
        HashMap<String, Integer> tempMap = new HashMap<>();
        ArrayList<double[]> tempValue = new ArrayList<>();
        for (String line : FileUtils.getLineStream(embeddingFile, "UTF-8")) {
            String[] arr = line.split(" ");
            double[] embedding = new double[DIM];
            for (int i = 0; i < DIM; ++i) {
                embedding[i] = Double.parseDouble(arr[i + 1]);
            }
            tempMap.put(arr[0], tempMap.size());
            tempValue.add(embedding);
        }
        EMBEDDING_ID = tempMap;
        EMBEDDING_VALUE = tempValue;
    }

    // Normally cosine gives a value from -1 to 1. However, we normalize this value to 0 -> 1
    public static double cosineDistance(String a, String b) {
        if (EMBEDDING_ID == null) {
            init();
        }

        if (a.equalsIgnoreCase(b)) {
            return 0;
        }

        Integer aId = EMBEDDING_ID.get(a);
        if (aId == null) {
            return -1;
        }
        Integer bId = EMBEDDING_ID.get(b);
        if (bId == null) {
            return -1;
        }

        if (aId > bId) {
            Integer tmp = aId;
            aId = bId;
            bId = tmp;
        }

        long key = ((long) aId) * EMBEDDING_VALUE.size() + bId;
        Double r = CACHE_COSINE.get(key);
        if (r != null) {
            return r;
        }

        double[] aEmbedding = EMBEDDING_VALUE.get(aId);
        double[] bEmbedding = EMBEDDING_VALUE.get(bId);

        double dotProduct = 0;
        double aLength = 0, bLength = 0;
        for (int i = 0; i < DIM; ++i) {
            dotProduct += aEmbedding[i] * bEmbedding[i];
            aLength += aEmbedding[i] * aEmbedding[i];
            bLength += bEmbedding[i] * bEmbedding[i];
        }

        // Normalize.
        double cosine = 0.5 - dotProduct / Math.sqrt(aLength) / Math.sqrt(bLength) / 2;

        CACHE_COSINE.put(key, cosine);
        return cosine;
    }

    public static double[] getEmbedding(String w) {
        if (EMBEDDING_ID == null) {
            init();
        }
        Integer wId = EMBEDDING_ID.get(w);
        if (wId == null) {
            return null;
        }
        return EMBEDDING_VALUE.get(wId);
    }

    public static void main(String[] args) {
    }
}
