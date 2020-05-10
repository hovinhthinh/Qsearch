package server.table.experimental;

import nlp.NLP;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import server.table.QfactLight;
import server.table.TableQfactLoader;
import util.Gson;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Deprecated
public class EntityQfactHandler extends AbstractHandler {
    public static final Logger LOGGER = Logger.getLogger(EntityQfactHandler.class.getName());

    static ArrayList<QfactLight> qfacts = TableQfactLoader.load();

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException {
        request.setHandled(true);
        httpServletResponse.setCharacterEncoding("utf-8");

        // Get parameters
        String entityConstraint = NLP.stripSentence(request.getParameter("entity")).toLowerCase();

        ArrayList<List<QfactLight>> result = new ArrayList<>();
        if (!entityConstraint.isEmpty()) {
            for (int i = 0; i < qfacts.size(); ++i) {
                if (qfacts.get(i).entity.substring(5).replace('_', ' ').toLowerCase().contains(entityConstraint)) {
                    int j = i;
                    while (j < qfacts.size() - 1 && qfacts.get(j + 1).entity.equals(qfacts.get(j).entity)) {
                        ++j;
                    }
                    result.add(qfacts.subList(i, j + 1));
                    int pivot = 0;
                    if (result.size() > 100) {
                        for (int k = 1; k < result.size(); ++k) {
                            if (result.get(k).size() < result.get(pivot).size()) {
                                pivot = k;
                            }
                        }
                        result.set(pivot, result.get(result.size() - 1));
                        result.remove(result.size() - 1);
                    }
                    i = j;
                }
            }
        }
        Collections.sort(result, (o1, o2) -> Integer.compare(o2.size(), o1.size()));

        httpServletResponse.getWriter().print(Gson.toJson(result));

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }
}
