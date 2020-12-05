package server.table.handler;

import org.json.JSONArray;
import org.json.JSONObject;
import server.table.QfactLight;
import server.table.TableQfactLoader;
import util.FileUtils;
import util.Gson;
import util.Pair;
import yago.TaxonomyGraph;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

public class TypeSuggestionHandler extends HttpServlet {
    public static final Logger LOGGER = Logger.getLogger(TypeSuggestionHandler.class.getName());
    public static final int N_TOP_SUGGESTION = 10;

    public static ArrayList<Pair<String, Integer>> typeToFreq = new ArrayList<>();

    static {
        load(10);
    }

    // Use for analyzing.
    private static void analyzeAndSaveToFile() {
        HashMap<String, Integer> specificTypeStats = new HashMap<>();

        ArrayList<QfactLight[]> qfacts = TableQfactLoader.load();
        for (int i = 0; i < qfacts.size(); ++i) {
            String entity = qfacts.get(i)[0].entity;
            // process type
            for (String type : TaxonomyGraph.getDefaultGraphInstance().getTextualizedTypes("<" + entity.substring(5) + ">", true)) {
                specificTypeStats.put(type, specificTypeStats.getOrDefault(type, 0) + 1);
            }
        }

        ArrayList<Pair<String, Integer>> type2freq = new ArrayList<>();
        for (Map.Entry<String, Integer> e : specificTypeStats.entrySet()) {
            type2freq.add(new Pair(e.getKey(), e.getValue()));
        }
        Collections.sort(type2freq, (a, b) -> b.second.compareTo(a.second));

        PrintWriter out = FileUtils.getPrintWriter("data/table/type_wiki+tablem.gz", "UTF-8");
        for (Pair<String, Integer> p : type2freq) {
            out.println(p.first + "\t" + p.second);
        }
        out.close();
    }

    public static void load(int nEntityThreshold) {
        for (String line : FileUtils.getLineStream("data/table/type_wiki+tablem.gz", "UTF-8")) {
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
        Collections.sort(suggestions, (a, b) -> b.second.compareTo(a.second));
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
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        String typePrefix = request.getParameter("prefix");
        if (typePrefix == null) {
            httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        typePrefix = typePrefix.toLowerCase().replaceAll("\\s++", " ").replaceAll("^\\s++", "");

        JSONObject response = new JSONObject().put("prefix", typePrefix).put("suggestions", new JSONArray(Gson.toJson(suggest(typePrefix, N_TOP_SUGGESTION))));
        httpServletResponse.getWriter().print(response.toString());

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static void main(String[] args) {
        analyzeAndSaveToFile();
//        System.out.println(new Gson().toJson(TypeSuggestionHandler.suggest("business", 7)));
    }
}
