package server.table.experimental;

import nlp.NLP;
import org.json.JSONObject;
import server.common.handler.ResultCacheHandler;
import server.table.QfactLight;
import server.table.TableQfactLoader;
import server.table.TableQuery;
import storage.table.index.TableIndex;
import storage.table.index.TableIndexStorage;
import util.Gson;
import util.Pair;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class BrowsingResult {
    public ArrayList<List<QfactLight>> result = new ArrayList<>();
    public HashMap<String, TableIndex> tableId2Index = new HashMap<>();
}

public class EntityQfactHandler extends HttpServlet {
    public static final Logger LOGGER = Logger.getLogger(EntityQfactHandler.class.getName());

    static ArrayList<QfactLight[]> qfacts = TableQfactLoader.load();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        httpServletResponse.setCharacterEncoding("utf-8");

        // Get parameters
        String entityConstraint = NLP.stripSentence(request.getParameter("entity")).toLowerCase();
        ArrayList<String> contextConstraint = NLP.splitSentence(request.getParameter("context").toLowerCase());

        BrowsingResult r = new BrowsingResult();
        if (!entityConstraint.isEmpty()) {
            for (int i = 0; i < qfacts.size(); ++i) {
                if (qfacts.get(i)[0].entity.substring(5).replace('_', ' ').toLowerCase().contains(entityConstraint)) {
                    r.result.add(Arrays.asList(qfacts.get(i)));
                    int pivot = 0;
                    if (r.result.size() > 100) {
                        for (int k = 1; k < r.result.size(); ++k) {
                            if (r.result.get(k).size() < r.result.get(pivot).size()) {
                                pivot = k;
                            }
                        }
                        r.result.set(pivot, r.result.get(r.result.size() - 1));
                        r.result.remove(r.result.size() - 1);
                    }
                }
            }
        }
        Collections.sort(r.result, (o1, o2) -> Integer.compare(o2.size(), o1.size()));
        for (List<QfactLight> l : r.result) {
            for (QfactLight f : l) {
                if (!r.tableId2Index.containsKey(f.tableId)) {
                    r.tableId2Index.put(f.tableId, TableIndexStorage.get(f.tableId));
                }
            }
        }

        HashMap<String, Object> matchCache = new HashMap<>();
        for (int i = 0; i < r.result.size(); ++i) {
            r.result.set(i, r.result.get(i).stream()
                    .map(f -> new Pair<>(f, TableQuery.match(new ArrayList<>(), contextConstraint, f, r.tableId2Index.get(f.tableId), null, matchCache).first))
                    .sorted((a, b) -> b.second.compareTo(a.second))
                    .map(o -> o.first)
                    .collect(Collectors.toCollection(ArrayList::new))
            );
        }

        String sessionKey = ResultCacheHandler.addResult(Gson.toJson(r));
        httpServletResponse.getWriter().print(new JSONObject().append("s", sessionKey).toString());
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }
}
