package nlp;

import util.FileUtils;

import java.util.HashMap;
import java.util.Map;

// Return a non-negative value. A value of 0 means that the 2 string are the same.
// A value of -1 means that similarity cannot be computed (embedding is unavailable).
public class Glove {
    public static final String DATA_DIR = "./resources/glove/";
    public static final int DIM = 100; // 50, 100, 200 or 300.

    private static Map<String, double[]> EMBEDDING = null;

    private synchronized static void init() {
        if (EMBEDDING != null) {
            return;
        }
        String embeddingFile = DATA_DIR + "glove.6B." + DIM + "d.txt";
        Map<String, double[]> tempMap = new HashMap<>();
        for (String line : FileUtils.getLineStream(embeddingFile, "UTF-8")) {
            String[] arr = line.split(" ");
            double[] embedding = new double[DIM];
            for (int i = 0; i < DIM; ++i) {
                embedding[i] = Double.parseDouble(arr[i + 1]);
            }
            tempMap.put(arr[0], embedding);
        }
        EMBEDDING = tempMap;
    }

    // 0 -> +oo
    public static double euclideanDistance(String a, String b) {
        if (EMBEDDING == null) {
            init();
        }

        if (a.equalsIgnoreCase(b)) {
            return 0;
        }

        double[] aEmbedding = EMBEDDING.get(a);
        if (aEmbedding == null) {
            return -1;
        }

        double[] bEmbedding = EMBEDDING.get(b);
        if (bEmbedding == null) {
            return -1;
        }

        double result = 0;
        for (int i = 0; i < DIM; ++i) {
            result += Math.pow(aEmbedding[i] - bEmbedding[i], 2);
        }
        return Math.sqrt(result);
    }

    // Normally cosine gives a value from -1 to 1. However, we normalize this value to 0 -> 1
    public static double cosineDistance(String a, String b) {
        if (EMBEDDING == null) {
            init();
        }

        if (a.equalsIgnoreCase(b)) {
            return 0;
        }

        double[] aEmbedding = EMBEDDING.get(a);
        if (aEmbedding == null) {
            return -1;
        }

        double[] bEmbedding = EMBEDDING.get(b);
        if (bEmbedding == null) {
            return -1;
        }

        double dotProduct = 0;
        double aLength = 0, bLength = 0;
        for (int i = 0; i < DIM; ++i) {
            dotProduct += aEmbedding[i] * bEmbedding[i];
            aLength += aEmbedding[i] * aEmbedding[i];
            bLength += bEmbedding[i] * bEmbedding[i];
        }

        double cosine = dotProduct / Math.sqrt(aLength) / Math.sqrt(bLength);

        // Normalize.
        return 0.5 - cosine / 2;
    }

    public static double[] getEmbedding(String w) {
        if (EMBEDDING == null) {
            init();
        }
        return EMBEDDING.get(w);
    }

    public static void main(String[] args) {
    }
}
