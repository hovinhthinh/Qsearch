package data;

import model.context.IDF;
import util.FileUtils;

import java.io.PrintWriter;
import java.util.*;

@Deprecated
public class DfSummary {
    public static void main(String[] args) {
        args = "/home/hvthinh/datasets/STICS/df.gz /home/hvthinh/datasets/NYT/df.gz ./data/df_stics+nyt.gz".split(
                " ");
        HashMap<String, Integer> word2doc = new HashMap<>();
        for (int i = 0; i < args.length - 1; ++i) {
            for (String line : FileUtils.getLineStream(args[i], "UTF-8")) {
                String[] arr = line.split(" ");
                word2doc.put(arr[0], word2doc.getOrDefault(arr[0], 0) + Integer.parseInt(arr[1]));
            }
        }
        double nDoc = word2doc.get(IDF._N_DOC_NAME);
        ArrayList<Map.Entry<String, Integer>> arr = new ArrayList<>(word2doc.entrySet());
        Collections.sort(arr, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        PrintWriter out = FileUtils.getPrintWriter(args[args.length - 1], "UTF-8");
        arr.forEach(e -> {
            out.println(String.format("%s\t%d", e.getKey(), e.getValue()));
        });
        out.close();
    }
}
