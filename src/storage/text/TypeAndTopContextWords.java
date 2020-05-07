package storage.text;

import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.headword.StringUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Deprecated
public class TypeAndTopContextWords {
    public static void process(Iterable<JSONObject> data, PrintWriter out, int nTop) throws Exception {
        HashMap<String, Integer> count = new HashMap<>();
        int nCount = 0;
        for (JSONObject o : data) {
            ++nCount;
            JSONArray facts = o.getJSONObject("_source").getJSONArray("facts");
            HashSet<String> entityContext = new HashSet<>();

            for (int i = 0; i < facts.length(); ++i) {
                if (i > 0 && i % 1000 == 0) {
                    System.out.printf("%d entities\t%d/%d facts\r\n", nCount, i, facts.length());
                }
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
            }
            HashSet<String> entityContextStem = new HashSet<>();

            for (String s : entityContext) {
                entityContextStem.add(StringUtils.stem(s.toLowerCase(), Morpha.any));
            }
            for (String s : entityContextStem) {
                count.put(s, count.getOrDefault(s, 0) + 1);
            }
        }

        ArrayList<Map.Entry<String, Integer>> sorted = new ArrayList<>(count.entrySet());
        sorted.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        out.print("{ ");
        for (Map.Entry e : sorted.subList(0, Math.min(nTop, sorted.size()))) {
            out.printf("%s ", e.getKey());
        }
        out.println("}");
        out.flush();
    }

    public static void main(String[] args) throws Exception {
        int nTop = 100;
        PrintWriter out = FileUtils.getPrintWriter("output/yago_type_topcontext.txt", "UTF-8");
        for (String line : FileUtils.getLineStream("output/yago_type_stats.txt")) {
            String type = line.split(" ")[0];
            int count = Integer.parseInt(line.split(" ")[1]);
            if (count < 1000 || count >= 100000) {
                continue;
            }
            System.out.println("Fetching: " + type);
            Iterable<JSONObject> data = ElasticSearchQuery.searchByType(type);
            System.out.println("Processing: " + type);
            out.print(line + "\t");
            if (data == null) {
                throw new Exception("Fail: " + type);
            }
            process(data, out, nTop);
        }
        out.close();
    }
}
