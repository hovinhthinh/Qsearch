package nlp;

import config.Configuration;
import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import util.FileUtils;

import java.util.ArrayList;

// Return a non-negative value. A value of 0 means that the 2 strings are the same.
// A value of -1 means that similarity cannot be computed (embedding is unavailable).
//@SuppressWarnings("The caching mechanism is not thread safe")
public class Glove {
    public static final String DATA_DIR = Configuration.get("glove.folder_path");
    public static final int DIM = 100; // 50, 100, 200 or 300.

    private static Object2IntOpenHashMap<String> EMBEDDING_ID = null;
    private static ArrayList<double[]> EMBEDDING_VALUE = null;

    public static final boolean USE_CACHE = true;
    private static final int CACHE_COSINE_SIZE = 10000000;
    private static Long2DoubleLinkedOpenHashMap CACHE_COSINE = USE_CACHE ? new Long2DoubleLinkedOpenHashMap(CACHE_COSINE_SIZE) : null;

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

        if (USE_CACHE) {
            CACHE_COSINE.defaultReturnValue(-1.0);
        }
    }

    // Normally cosine gives a value from -1 to 1. However, we normalize this value to 0 -> 1
    public static double cosineDistance(String a, String b) {
        if (EMBEDDING_ID == null) {
            init();
        }

        if (a.equalsIgnoreCase(b)) {
            return 0;
        }

        try {
            // if a and b are both numbers, but not equal, then return a default value.
            Double.parseDouble(a);
            Double.parseDouble(b);
            return 0.4; // This is roughly equal to the average distance of top 10k popular words.
        } catch (Exception e) {
        }

        int aId, bId;
        if ((aId = EMBEDDING_ID.getInt(a)) == -1 || (bId = EMBEDDING_ID.getInt(b)) == -1) {
            return -1;
        }

        long key;
        if (USE_CACHE) {
            key = aId < bId
                    ? ((long) aId) * EMBEDDING_VALUE.size() + bId
                    : ((long) bId) * EMBEDDING_VALUE.size() + aId;

            double r;
            synchronized (CACHE_COSINE) {
                r = CACHE_COSINE.getAndMoveToFirst(key);
            }
            if (r != -1.0) {
                return r;
            }
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

        if (USE_CACHE) {
            synchronized (CACHE_COSINE) {
                CACHE_COSINE.putAndMoveToFirst(key, cosine);
                if (CACHE_COSINE.size() > CACHE_COSINE_SIZE) {
                    CACHE_COSINE.removeLastDouble();
                }
            }
        }
        return cosine;
    }

    public static double[] getEmbedding(String w) {
        if (EMBEDDING_ID == null) {
            init();
        }
        int wId = EMBEDDING_ID.getInt(w);
        if (wId == -1) {
            return null;
        }
        return EMBEDDING_VALUE.get(wId);
    }

    public static void main(String[] args) {
    }
}
