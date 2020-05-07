package nlp;

import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import util.FileUtils;

import java.util.ArrayList;

// Return a non-negative value. A value of 0 means that the 2 strings are the same.
// A value of -1 means that similarity cannot be computed (embedding is unavailable).
@SuppressWarnings("The caching mechanism is not thread safe")
public class Glove {
    public static final String DATA_DIR = "./resources/glove/";
    public static final int DIM = 100; // 50, 100, 200 or 300.

    private static Object2IntOpenHashMap<String> EMBEDDING_ID = null;
    private static ArrayList<double[]> EMBEDDING_VALUE = null;

    private static final int CACHE_COSINE_SIZE = 10000000;
    private static Long2DoubleLinkedOpenHashMap CACHE_COSINE = new Long2DoubleLinkedOpenHashMap(CACHE_COSINE_SIZE);

    private synchronized static void init() {
        if (EMBEDDING_ID != null) {
            return;
        }
        String embeddingFile = DATA_DIR + "glove.6B." + DIM + "d.txt";
        Object2IntOpenHashMap<String> tempMap = new Object2IntOpenHashMap<>();
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
        EMBEDDING_ID.defaultReturnValue(-1);

        EMBEDDING_VALUE = tempValue;
        EMBEDDING_VALUE.trimToSize();

        CACHE_COSINE.defaultReturnValue(-1.0);
    }

    // Normally cosine gives a value from -1 to 1. However, we normalize this value to 0 -> 1
    public static double cosineDistance(String a, String b) {
        if (EMBEDDING_ID == null) {
            init();
        }

        if (a.equalsIgnoreCase(b)) {
            return 0;
        }

        int aId, bId;
        if ((aId = EMBEDDING_ID.getInt(a)) == -1 || (bId = EMBEDDING_ID.getInt(b)) == -1) {
            return -1;
        }

        if (aId > bId) {
            int tmp = aId;
            aId = bId;
            bId = tmp;
        }

        long key = ((long) aId) * EMBEDDING_VALUE.size() + bId;
        double r;
        synchronized (CACHE_COSINE) {
            r = CACHE_COSINE.getAndMoveToFirst(key);
        }
        if (r != -1.0) {
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

        synchronized (CACHE_COSINE) {
            CACHE_COSINE.putAndMoveToFirst(key, cosine);
            if (CACHE_COSINE.size() > CACHE_COSINE_SIZE) {
                CACHE_COSINE.removeLastDouble();
            }
        }
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
