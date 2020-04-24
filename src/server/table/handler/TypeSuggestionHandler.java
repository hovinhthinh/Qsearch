package server.table.handler;

import com.google.gson.Gson;
import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import storage.table.ElasticSearchTableImport;
import util.FileUtils;
import util.HTTPRequest;
import util.Pair;
import util.SelfMonitor;
import yago.TaxonomyGraph;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TypeSuggestionHandler extends AbstractHandler {
    public static final Logger LOGGER = Logger.getLogger(TypeSuggestionHandler.class.getName());
    private static Gson GSON = new Gson();


    private static ArrayList<Pair<String, Integer>> typeToFreq = new ArrayList<>();

    private int nTopSuggestion;

    public TypeSuggestionHandler(int nTopSuggestion) {
        this.nTopSuggestion = nTopSuggestion;
    }

    static {
        load(10);
    }


    // Use for analyzing
    private static void extractEntities(JSONObject o, HashSet<String> entities) {
        try {
            Table table = GSON.fromJson(o.getJSONObject("_source").getString("parsedJson"), Table.class);
            // for all Qfacts
            for (int qCol = 0; qCol < table.nColumn; ++qCol) {
                if (!table.isNumericColumn[qCol]) {
                    continue;
                }

                for (int row = 0; row < table.nDataRow; ++row) {
                    QuantityLink ql = table.data[row][qCol].getRepresentativeQuantityLink();
                    if (ql == null) {
                        continue;
                    }
                    EntityLink el = table.data[row][table.quantityToEntityColumn[qCol]].getRepresentativeEntityLink();
                    if (el == null) {
                        continue;
                    }

                    String entity = "<" + el.target.substring(el.target.lastIndexOf(":") + 1) + ">";
                    entities.add(entity);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Use for analyzing.
    private static void analyzeAndSaveToFile() {
        HashSet<String> entities = new HashSet<>();
        SelfMonitor monitor = new SelfMonitor(TypeSuggestionHandler.class.getName(), -1, 5);
        monitor.start();
        try {
            String url = ElasticSearchTableImport.PROTOCOL + "://" + ElasticSearchTableImport.ES_HOST + "/" + ElasticSearchTableImport.TABLE_INDEX + "/" + ElasticSearchTableImport.TABLE_TYPE + "/_search?scroll=15m";
            String body = "{\n" +
                    "  \"_source\": [\n" +
                    "    \"parsedJson\"\n" +
                    "  ],\n" +
                    "  \"size\": 8000,\n" +
                    "  \"query\": {\n" +
                    "    \"bool\": {\n" +
                    "      \"must\": [\n" +
                    "        {\n" +
                    "          \"exists\": {\n" +
                    "            \"field\": \"searchable\"\n" +
                    "          }\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";
            String data = HTTPRequest.POST(url, body);
            if (data == null) {
                throw new RuntimeException("cannot load type suggestion module: null data.");
            }
            JSONObject json = new JSONObject(data);
            JSONArray arr = json.getJSONObject("hits").getJSONArray("hits");
            for (int i = 0; i < arr.length(); ++i) {
                extractEntities(arr.getJSONObject(i), entities);
                monitor.incAndGet();
            }
            String scroll_id = json.getString("_scroll_id");
            url = ElasticSearchTableImport.PROTOCOL + "://" + ElasticSearchTableImport.ES_HOST + "/_search/scroll";
            body = "{\"scroll\":\"15m\",\"scroll_id\":\"" + scroll_id + "\"}";
            do {
                data = HTTPRequest.POST(url, body);
                if (data == null) {
                    throw new RuntimeException("cannot load type suggestion module: scroll_id expired.");
                }
                json = new JSONObject(data);
                arr = json.getJSONObject("hits").getJSONArray("hits");
                for (int i = 0; i < arr.length(); ++i) {
                    extractEntities(arr.getJSONObject(i), entities);
                    monitor.incAndGet();
                }
            } while (arr.length() > 0);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("cannot load type suggestion module: unknown exception.");
        }
        monitor.forceShutdown();

        // entities to types
        HashMap<String, Integer> specificTypeStats = new HashMap<>();
        for (String e : entities) {
            for (String type : TaxonomyGraph.getDefaultGraphInstance().getTextualizedTypes(e, false)) {
                specificTypeStats.put(type, specificTypeStats.getOrDefault(type, 0) + 1);
            }
        }

        PrintWriter out = FileUtils.getPrintWriter("./data/type_stics+nyt.gz", "UTF-8");
        for (Map.Entry<String, Integer> p : specificTypeStats.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList())) {
            out.println(p.getKey() + "\t" + p.getValue());
        }
        out.close();
    }

    private static void load(int nEntityThreshold) {
        for (String line : FileUtils.getLineStream("./data/type_stics+nyt.gz", "UTF-8")) {
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
        Collections.sort(suggestions, (a, b) -> {
            return b.second.compareTo(a.second);
        });
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
        System.out.println(new Gson().toJson(TypeSuggestionHandler.suggest("car ", 7)));
    }
}
