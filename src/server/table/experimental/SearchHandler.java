package server.table.experimental;

import com.google.gson.Gson;
import nlp.NLP;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import storage.table.experimental.ElasticSearchQuery;
import storage.table.experimental.ResultInstance;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Deprecated
public class SearchHandler extends AbstractHandler {
    public static final Logger LOGGER = Logger.getLogger(SearchHandler.class.getName());
    private static Gson GSON = new Gson();

    private int nTopResult;

    public SearchHandler(int nTopResult) {
        this.nTopResult = nTopResult;
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException {
        request.setHandled(true);

        // Get parameters
        String typeConstraint = request.getParameter("type");
        String contextConstraint = request.getParameter("context");
        String quantityConstraint = request.getParameter("quantity");

        Map additionalParams = new HashMap();

        String ntop = request.getParameter("ntop");
        int nResult = ntop != null ? Integer.parseInt(ntop) : nTopResult;

        httpServletResponse.setCharacterEncoding("utf-8");

        ArrayList<ResultInstance> response = search(nResult, typeConstraint, contextConstraint, quantityConstraint, additionalParams);

        synchronized (GSON) {
            httpServletResponse.getWriter().print(GSON.toJson(response));
        }
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static ArrayList<ResultInstance> search(int nTopResult, String typeConstraint, String contextConstraint, String quantityConstraint, Map additionalParameters) {
        // Optimize
        if (typeConstraint != null) {
            typeConstraint = NLP.stripSentence(typeConstraint).toLowerCase();
        }
        if (contextConstraint != null) {
            contextConstraint = NLP.stripSentence(contextConstraint).toLowerCase();
        }


        LOGGER.info("Query: {Type: \"" + typeConstraint + "\"; Context: \"" + contextConstraint +
                "\"; Quantity: \"" + null + "\"}");

        ArrayList<ResultInstance> response = ElasticSearchQuery.searchWithoutQuantityConstraint(typeConstraint, contextConstraint, quantityConstraint, additionalParameters);
        if (response.size() > nTopResult) {
            response.subList(nTopResult, response.size()).clear();
        }
        return response;
    }
}
