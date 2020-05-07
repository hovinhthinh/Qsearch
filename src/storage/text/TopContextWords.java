package storage.text;

import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.HTTPRequest;
import util.headword.StringUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Deprecated
public class TopContextWords {
    public static HashMap<String, Integer> context = new HashMap<>();

    // Here we can also compute per/domain blm.
//    public static HashMap<String, Integer> contextDimensionless = new HashMap<>();
//    public static HashMap<String, Integer> contextLength = new HashMap<>();
//    public static HashMap<String, Integer> contextMoney = new HashMap<>();
//    public static HashMap<String, Integer> contextDuration = new HashMap<>();
//    public static HashMap<String, Integer> contextPercentage = new HashMap<>();
    public static int eCount = 0;

    public static void processObject(JSONObject o) {
        try {
            System.out.println((++eCount) + ": Processing: " + o.getString("_id"));
            JSONArray facts = o.getJSONObject("_source").getJSONArray("facts");

            for (int i = 0; i < facts.length(); ++i) {
                HashSet<String> entityContext = new HashSet<>();
                String Q = facts.getJSONObject(i).getString("quantity");
                Quantity qt = Quantity.fromQuantityString(Q);
                JSONArray context = facts.getJSONObject(i).getJSONArray("context");
                for (int k = 0; k < context.length(); ++k) {
                    String ct = context.getString(k);
                    if (ct.startsWith("<T>:")) {
                        // handle time like normal terms.
                        entityContext.addAll(NLP.splitSentence(ct.substring(4)));
                    } else if (ct.startsWith("<E>:")) {
                        entityContext.addAll(NLP.splitSentence(ct.substring(4)));
                    } else {
                        entityContext.add(ct);
                    }
                }
                if (QuantityDomain.quantityMatchesDomain(qt, QuantityDomain.Domain.DIMENSIONLESS)) {
                    entityContext.addAll(NLP.splitSentence(qt.unit));
                }

                HashSet<String> entityContextStem = new HashSet<>();

                for (String s : entityContext) {
                    entityContextStem.add(StringUtils.stem(s.toLowerCase(), Morpha.any));
                }
                for (String s : entityContextStem) {
                    TopContextWords.context.put(s, TopContextWords.context.getOrDefault(s, 0) + 1);
                }
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
        ArrayList<Map.Entry<String, Integer>> sorted = new ArrayList<>(context.entrySet());
        sorted.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        PrintWriter out = FileUtils.getPrintWriter("data/blm_stics+nyt/all.gz", "UTF-8");
        for (Map.Entry<String, Integer> e : sorted) {
            out.println(e.getKey() + "\t" + e.getValue());
        }
        out.close();
    }
}
