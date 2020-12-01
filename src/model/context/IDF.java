package model.context;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import util.FileUtils;

// Computed from STIC+NYT corpus.
// TODO: Compute from WIKIPEDIA AS WELL ??
public class IDF {
    public static String _N_DOC_NAME = "_N_DOC";

    private static Object2DoubleOpenHashMap<String> DEFAULT_IDF;
    private static Object2DoubleOpenHashMap<String> ROBERTSON_IDF;
    private static double N_DOC;
    private static double MIN_IDF = 0.0001;
    private static double OOV_DEFAULT_IDF;
    private static double OOV_ROBERTSON_IDF;

    static {
        DEFAULT_IDF = new Object2DoubleOpenHashMap<>();
        ROBERTSON_IDF = new Object2DoubleOpenHashMap<>();
        // first line is N_DOC
        for (String line : FileUtils.getLineStream("./data/df_stics+nyt.gz", "UTF-8")) {
            String[] arr = line.split("\t");
            if (arr[0].equals(_N_DOC_NAME)) {
                N_DOC = Double.parseDouble(arr[1]);
            } else {
                double df = Double.parseDouble(arr[1]);
                DEFAULT_IDF.put(arr[0], Math.max(MIN_IDF, Math.log(N_DOC / (df + 1))));
                ROBERTSON_IDF.put(arr[0], Math.max(MIN_IDF, Math.log10((N_DOC - df + 0.5) / (df + 0.5))));
            }
        }
        OOV_DEFAULT_IDF = Math.max(MIN_IDF, Math.log(N_DOC / (0 + 1))); // df = 0
        OOV_ROBERTSON_IDF = Math.max(MIN_IDF, Math.log10((N_DOC - 0 + 0.5) / (0 + 0.5))); // df = 0
    }


    public static final double getDefaultIdf(String word) {
        return getDefaultIdf(word, true);
    }

    // word should be lowercase
    // if allowOOV = false, OOV will get MIN_IDF
    public static final double getDefaultIdf(String word, boolean allowOOV) {
        double idf = DEFAULT_IDF.getOrDefault(word, 0.0);
        if (idf == 0 && !allowOOV) {
            return MIN_IDF;
        }
        return idf != 0 ? idf : OOV_DEFAULT_IDF;
    }


    public static final double getRobertsonIdf(String word) {
        return getRobertsonIdf(word, true);
    }

    // word should be lowercase
    // if allowOOV = false, OOV will get MIN_IDF
    public static final double getRobertsonIdf(String word, boolean allowOOV) {
        double idf = ROBERTSON_IDF.getOrDefault(word, 0.0);
        if (idf == 0 && !allowOOV) {
            return MIN_IDF;
        }
        return idf != 0 ? idf : OOV_ROBERTSON_IDF;
    }

}
