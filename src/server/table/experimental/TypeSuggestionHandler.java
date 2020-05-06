package server.table.experimental;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import util.FileUtils;
import util.Gson;
import util.Pair;
import yago.TaxonomyGraph;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

@Deprecated
public class TypeSuggestionHandler extends AbstractHandler {
    public static final Logger LOGGER = Logger.getLogger(TypeSuggestionHandler.class.getName());

    private static ArrayList<Pair<String, Integer>> typeToFreq = new ArrayList<>();

    private int nTopSuggestion;

    public TypeSuggestionHandler(int nTopSuggestion) {
        this.nTopSuggestion = nTopSuggestion;
    }

    // Use for analyzing.
    private static void analyzeAndSaveToFile() {
        HashMap<String, Integer> specificTypeStats = new HashMap<>();

        ArrayList<Qfact> qfacts = TableQfactSaver.load();
        for (int i = 0; i < qfacts.size(); ++i) {
            if (i > 0 && qfacts.get(i).entity.equals(qfacts.get(i - 1).entity)) {
                continue;
            }
            String entity = qfacts.get(i).entity;

            int j = i;
            while (j < qfacts.size() - 1 && qfacts.get(j + 1).entity.equals(entity)) {
                ++j;
            }
            // process type
            HashSet<String> specificTypeSet = new HashSet<>();
            Int2IntLinkedOpenHashMap typeSet = TaxonomyGraph.getDefaultGraphInstance().getType2DistanceMapForEntity(
                    TaxonomyGraph.getDefaultGraphInstance().entity2Id.get("<" + entity.substring(5) + ">")
            );
            for (int typeId : typeSet.keySet()) {
                specificTypeSet.add(TaxonomyGraph.getDefaultGraphInstance().id2TextualizedType.get(typeId));
            }
            for (String type : specificTypeSet) {
                specificTypeStats.put(type, specificTypeStats.getOrDefault(type, 0) + 1);
            }
            // done processing
            i = j;
        }

        ArrayList<Pair<String, Integer>> type2freq = new ArrayList<>();
        for (Map.Entry<String, Integer> e : specificTypeStats.entrySet()) {
            type2freq.add(new Pair(e.getKey(), e.getValue()));
        }
        Collections.sort(type2freq, (a, b) -> b.second.compareTo(a.second));

        PrintWriter out = FileUtils.getPrintWriter("./data/type_table_wiki+tablem.gz", "UTF-8");
        for (Pair<String, Integer> p : type2freq) {
            out.println(p.first + "\t" + p.second);
        }
        out.close();
    }

    public static void load(int nEntityThreshold) {
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
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException, ServletException {
        request.setHandled(true);

        String typePrefix = request.getParameter("prefix");
        if (typePrefix == null) {
            httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        typePrefix = typePrefix.toLowerCase().replaceAll("\\s++", " ").replaceAll("^\\s++", "");

        JSONObject response = new JSONObject().put("prefix", typePrefix).put("suggestions", new JSONArray(Gson.toJson(suggest(typePrefix, nTopSuggestion))));
        httpServletResponse.getWriter().print(response.toString());

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static void main(String[] args) {
        analyzeAndSaveToFile();
//        load(10);
//        System.out.println(new Gson().toJson(TypeSuggestionHandler.suggest("business", 7)));
    }
}
