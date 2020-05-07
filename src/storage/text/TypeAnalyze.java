package storage.text;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.HTTPRequest;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

@Deprecated
public class TypeAnalyze {
    public static HashMap<String, Integer> typeStats = new HashMap<>();
    public static HashMap<String, Integer> specificTypeStats = new HashMap<>();

    public static void processObject(JSONObject o) {
        HashSet<String> typeSet = new HashSet<>();
        HashSet<String> specificTypeSet = new HashSet<>();
        try {
            System.out.println("Processing: " + o.getString("_id"));
            JSONArray types = o.getJSONObject("_source").getJSONArray("types");
            for (int i = 0; i < types.length(); ++i) {
                String t = types.getJSONObject(i).getString("value");
                t = NLP.fastStemming(t, Morpha.noun);
                specificTypeSet.add(t);
                t = NLP.getHeadWord(t, false);
                typeSet.add(t);
            }
            for (String type : typeSet) {
                typeStats.put(type, typeStats.getOrDefault(type, 0) + 1);
            }
            for (String type : specificTypeSet) {
                specificTypeStats.put(type, specificTypeStats.getOrDefault(type, 0) + 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            String url = ElasticSearchDataImport.PROTOCOL + "://" + ElasticSearchDataImport.ES_HOST + "/" + ElasticSearchDataImport.INDEX + "/" + ElasticSearchDataImport.TYPE + "/_search?scroll=15m";
            String body = "{\"size\":1000,\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"searchable\"}}]}}}";
            String data = HTTPRequest.POST(url, body);
            if (data == null) {
                return;
            }
            JSONObject json = new JSONObject(data);
            JSONArray arr = json.getJSONObject("hits").getJSONArray("hits");
            for (int i = 0; i < arr.length(); ++i) {
                processObject(arr.getJSONObject(i));
            }
            String scroll_id = json.getString("_scroll_id");
            url = ElasticSearchDataImport.PROTOCOL + "://" + ElasticSearchDataImport.ES_HOST + "/_search/scroll";
            body = "{\"scroll\":\"15m\",\"scroll_id\":\"" + scroll_id + "\"}";
            do {
                data = HTTPRequest.POST(url, body);
                if (data == null) {
                    System.err.println("scroll_id expired.");
                    return;
                }
                json = new JSONObject(data);
                arr = json.getJSONObject("hits").getJSONArray("hits");
                for (int i = 0; i < arr.length(); ++i) {
                    processObject(arr.getJSONObject(i));
                }
            } while (arr.length() > 0);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        ArrayList<Map.Entry<String, Integer>> arr =
                typeStats.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue()))
                        .collect(Collectors.toCollection(ArrayList::new));
        PrintWriter out = FileUtils.getPrintWriter("output/yago_type_stats.txt", "UTF-8");
        for (Map.Entry<String, Integer> e : arr) {
            out.println(e.getKey() + " " + e.getValue());
        }
        out.close();

        arr = specificTypeStats.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
        out = FileUtils.getPrintWriter("output/yago_type_specific_stats.txt", "UTF-8");
        for (Map.Entry<String, Integer> e : arr) {
            out.println(e.getKey() + " " + e.getValue());
        }
        out.close();
    }
}
