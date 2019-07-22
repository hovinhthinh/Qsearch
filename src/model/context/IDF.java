package model.context;

import data.DfSummary;
import util.FileUtils;

import java.util.HashMap;
import java.util.Map;


// Computed from STIC+NYT corpus.
public class IDF {
    private static Map<String, Double> DF;
    private static double N_DOC;
    private static double MIN_IDF = 0.0001;

    static {
        DF = new HashMap<>();
        N_DOC = -1;
        for (String line : FileUtils.getLineStream("./data/df_stics+nyt.gz", "UTF-8")) {
            String[] arr = line.split("\t");
            if (arr[0].equals(DfSummary._N_DOC)) {
                N_DOC = Double.parseDouble(arr[1]);
            } else {
                DF.put(arr[0], Double.parseDouble(arr[1]));
            }
        }
    }

    // word should be lowercase
    public static double getDefaultIdf(String word) {
        double df = DF.getOrDefault(word, 0.0);
        return Math.max(MIN_IDF, Math.log(N_DOC / (df + 1)));
    }

    // word should be lowercase
    public static double getRobertsonIdf(String word) {
        double df = DF.getOrDefault(word, 0.0);
        return Math.max(MIN_IDF, Math.log10((N_DOC - df + 0.5) / (df + 0.5)));
    }
}
