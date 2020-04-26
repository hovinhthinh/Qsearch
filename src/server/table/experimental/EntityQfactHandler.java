package server.table.experimental;

import com.google.gson.Gson;
import nlp.NLP;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import util.FileUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

@Deprecated
public class EntityQfactHandler extends AbstractHandler {
    public static final Logger LOGGER = Logger.getLogger(EntityQfactHandler.class.getName());
    private static Gson GSON = new Gson();

    static class Qfact {
        String entity;
        String entityForSearch;
        String context;
        String quantity;
        double score;
        String domain;
        String source;
    }

    static ArrayList<Qfact> qfacts;

    static {
        qfacts = new ArrayList<>();
        for (String line : FileUtils.getLineStream("/GW/D5data-12/hvthinh/TabQs/annotation+linking/wiki+tablem_qfacts.gz")) {
            String[] arr = line.split("\t");
            Qfact f = new Qfact();
            f.entity = arr[0];
            f.context = arr[1];
            f.quantity = arr[2];
            f.score = Double.parseDouble(arr[3]);
            f.domain = arr[4];
            f.source = arr[5];
            f.entityForSearch = arr[0].substring(5).replace('_', ' ').toLowerCase();
            qfacts.add(f);
        }
        Collections.sort(qfacts, (o1, o2) -> {
            int x = o1.entity.compareTo(o2.entity);
            if (x != 0) {
                return x;
            }
            return Double.compare(o2.score, o1.score);
        });
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException {
        request.setHandled(true);
        httpServletResponse.setCharacterEncoding("utf-8");

        // Get parameters
        String entityConstraint = NLP.stripSentence(request.getParameter("entity")).toLowerCase();

        HashMap<String, List<Qfact>> result = new HashMap<>();
        if (!entityConstraint.isEmpty()) {
            for (int i = 0; i < qfacts.size(); ++i) {
                if (qfacts.get(i).entityForSearch.contains(entityConstraint)) {
                    int j = i;
                    while (j < qfacts.size() - 1 && qfacts.get(j + 1).entity.equals(qfacts.get(j).entity)) {
                        ++j;
                    }
                    if (result.size() < 100) {
                        result.put(qfacts.get(i).entity, qfacts.subList(i, j + 1));
                    }
                    i = j;
                }
            }
        }

        synchronized (GSON) {
            httpServletResponse.getWriter().print(GSON.toJson(result));
        }
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }
}
