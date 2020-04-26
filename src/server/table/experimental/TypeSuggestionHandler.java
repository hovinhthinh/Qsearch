package server.table.experimental;

import com.google.gson.Gson;
import nlp.NLP;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import storage.table.experimental.ElasticSearchDataImport;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.HTTPRequest;
import util.Pair;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;


public class TypeSuggestionHandler extends AbstractHandler {
    public static final Logger LOGGER = Logger.getLogger(TypeSuggestionHandler.class.getName());
    private Gson GSON = new Gson();


    private static ArrayList<Pair<String, Integer>> typeToFreq = new ArrayList<>();

    private int nTopSuggestion;

    public TypeSuggestionHandler(int nTopSuggestion) {
        this.nTopSuggestion = nTopSuggestion;

    }

    static {
        load(10);
    }

    // Use for analyzing.
    private static void processObject(JSONObject o, HashMap<String, Integer> specificTypeStats) {
        HashSet<String> specificTypeSet = new HashSet<>();
        try {
            System.out.println("Processing: " + o.getString("_id"));
            JSONArray types = o.getJSONObject("_source").getJSONArray("types");
            for (int i = 0; i < types.length(); ++i) {
                String t = types.getJSONObject(i).getString("value");
                t = NLP.fastStemming(t, Morpha.noun);
                specificTypeSet.add(t);
            }
            for (String type : specificTypeSet) {
                specificTypeStats.put(type, specificTypeStats.getOrDefault(type, 0) + 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Use for analyzing.
    private static void analyzeAndSaveToFile() {
        HashMap<String, Integer> specificTypeStats = new HashMap<>();
        ArrayList<Pair<String, Integer>> type2freq = new ArrayList<>();
        try {
            String url = ElasticSearchDataImport.PROTOCOL + "://" + ElasticSearchDataImport.ES_HOST + "/" + ElasticSearchDataImport.INDEX + "/" + ElasticSearchDataImport.TYPE + "/_search?scroll=15m";
            String body = "{\"size\":1000,\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"searchable\"}}]}}}";
            String data = HTTPRequest.POST(url, body);
            if (data == null) {
                throw new RuntimeException("cannot load type suggestion module: null data.");
            }
            JSONObject json = new JSONObject(data);
            JSONArray arr = json.getJSONObject("hits").getJSONArray("hits");
            for (int i = 0; i < arr.length(); ++i) {
                processObject(arr.getJSONObject(i), specificTypeStats);
            }
            String scroll_id = json.getString("_scroll_id");
            url = ElasticSearchDataImport.PROTOCOL + "://" + ElasticSearchDataImport.ES_HOST + "/_search/scroll";
            body = "{\"scroll\":\"15m\",\"scroll_id\":\"" + scroll_id + "\"}";
            do {
                data = HTTPRequest.POST(url, body);
                if (data == null) {
                    throw new RuntimeException("cannot load type suggestion module: scroll_id expired.");
                }
                json = new JSONObject(data);
                arr = json.getJSONObject("hits").getJSONArray("hits");
                for (int i = 0; i < arr.length(); ++i) {
                    processObject(arr.getJSONObject(i), specificTypeStats);
                }
            } while (arr.length() > 0);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("cannot load type suggestion module: unknown exception.");
        }

        for (Map.Entry<String, Integer> e : specificTypeStats.entrySet()) {
            type2freq.add(new Pair(e.getKey(), e.getValue()));
        }
        Collections.sort(type2freq, (a, b) -> {
            return a.first.compareTo(b.first);
        });

        PrintWriter out = FileUtils.getPrintWriter("./data/type_table_wiki+tablem.gz", "UTF-8");
        for (Pair<String, Integer> p : type2freq) {
            out.println(p.first + "\t" + p.second);
        }
        out.close();
    }

    private static void load(int nEntityThreshold) {
        for (String line : FileUtils.getLineStream("./data/type_table_wiki+tablem.gz", "UTF-8")) {
            String[] arr = line.split("\t");
            int freq = Integer.parseInt(arr[1]);
            if (freq >= nEntityThreshold) {
                typeToFreq.add(new Pair(arr[0], freq));
            }
        }
        Collections.sort(typeToFreq, (a, b) -> a.first.compareTo(b.first));

        LOGGER.info("loading type suggestion done. total " + typeToFreq.size() + " types.");
    }

    public static List<Pair<String, Integer>> suggest(String prefix, int nTopSuggestion) {
        ArrayList<Pair<String, Integer>> suggestions = new ArrayList<>();
        int l = -1, r = typeToFreq.size();
        while (l + 1 < r) {
            int mid = (l + r) >> 1;
            if (typeToFreq.get(mid).first.compareTo(prefix) >= 0) {
                r = mid;
            } else {
                l = mid;
            }
        }

        while (r < typeToFreq.size() && typeToFreq.get(r).first.startsWith(prefix)) {
            suggestions.add(typeToFreq.get(r++));
        }
        if (suggestions.size() > nTopSuggestion) {
            return suggestions.subList(0, nTopSuggestion);
        } else {
            return suggestions;
        }
    }

    // return -1 on not found
    public static int getTypeFreq(String type) {
        int l = -1, r = typeToFreq.size();
        while (l + 1 < r) {
            int mid = (l + r) >> 1;
            if (typeToFreq.get(mid).first.compareTo(type) >= 0) {
                r = mid;
            } else {
                l = mid;
            }
        }
        if (r >= typeToFreq.size()) {
            return -1;
        }
        Pair<String, Integer> t2f = typeToFreq.get(r);
        return t2f.first.equals(type) ? t2f.second : -1;
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException, ServletException {
        request.setHandled(true);

        String typePrefix = request.getParameter("prefix");
        if (typePrefix == null) {
            httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        typePrefix = typePrefix.toLowerCase().replaceAll("\\s++", " ").replaceAll("^\\s++", "");

        synchronized (GSON) {
            JSONObject response = new JSONObject().put("prefix", typePrefix).put("suggestions", new JSONArray(GSON.toJson(suggest(typePrefix, nTopSuggestion))));
            httpServletResponse.getWriter().print(response.toString());
        }
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static void main(String[] args) {
//        analyzeAndSaveToFile();
        System.out.println(new Gson().toJson(TypeSuggestionHandler.suggest("business", 7)));
    }
}
