package server.table.experimental.gui.search;

import model.quantity.QuantityConstraint;
import model.query.SimpleQueryParser;
import nlp.NLP;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import server.table.experimental.gui.ResultInstance;
import server.table.experimental.gui.TableQuery;
import util.Gson;
import util.Pair;
import util.Triple;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SearchHandler extends AbstractHandler {
    public static final Logger LOGGER = Logger.getLogger(SearchHandler.class.getName());

    private int nTopResult;

    public SearchHandler(int nTopResult) {
        this.nTopResult = nTopResult;
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException, ServletException {
        request.setHandled(true);

        // Get parameters
        String typeConstraint = request.getParameter("type");
        String contextConstraint = request.getParameter("context");
        String quantityConstraint = request.getParameter("quantity");
        String fullConstraint = request.getParameter("full");

        Map additionalParams = new HashMap();
        if (request.getParameter("corpus") != null) {
            additionalParams.put("corpus", request.getParameter("corpus"));
        }

        String ntop = request.getParameter("ntop");
        int nResult = ntop != null ? Integer.parseInt(ntop) : nTopResult;

        httpServletResponse.setCharacterEncoding("utf-8");

        SearchResult response = search(nResult, fullConstraint, typeConstraint, contextConstraint, quantityConstraint, additionalParams);

        httpServletResponse.getWriter().print(Gson.toJson(response));

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static SearchResult search(int nTopResult, String fullConstraint,
                                      String typeConstraint, String contextConstraint, String quantityConstraint,
                                      Map additionalParameters) {
        // Optimize
        if (typeConstraint != null) {
            typeConstraint = NLP.stripSentence(typeConstraint).toLowerCase();
        }
        if (contextConstraint != null) {
            contextConstraint = NLP.stripSentence(contextConstraint).toLowerCase();
        }
        if (quantityConstraint != null) {
            quantityConstraint = NLP.stripSentence(quantityConstraint).toLowerCase();
        }
        if (fullConstraint != null) {
            fullConstraint = NLP.stripSentence(fullConstraint).toLowerCase();
        }

        if (fullConstraint == null) {
            LOGGER.info("Query: {Type: \"" + typeConstraint + "\"; Context: \"" + contextConstraint +
                    "\"; Quantity: \"" + quantityConstraint + "\"}");
        } else {
            LOGGER.info("Query: {Full: \"" + fullConstraint + "\"}");
        }
        SearchResult response = new SearchResult();
        try {
            if (fullConstraint != null) {
                Triple<String, String, String> parsed = SimpleQueryParser.parse(fullConstraint, false);
                if (parsed == null) {
                    response.verdict = "Cannot parse full query.";
                } else {
                    typeConstraint = parsed.first;
                    contextConstraint = parsed.second;
                    quantityConstraint = parsed.third;
                    LOGGER.info("Parsed query: {Type: \"" + typeConstraint + "\"; Context: \"" + contextConstraint +
                            "\"; Quantity: \"" + quantityConstraint + "\"}");
                }
            }
            if (response.verdict == null) {
                Pair<QuantityConstraint, ArrayList<ResultInstance>> result =
                        TableQuery.search(typeConstraint, contextConstraint, quantityConstraint, additionalParameters);
                if (result.first == null) {
                    response.verdict = "Cannot detect quantity constraint.";
                } else if (result.second == null) {
                    response.verdict = "Search error."; // Should not happen!!!!!
                } else {
                    response.typeConstraint = typeConstraint;
                    response.contextConstraint = contextConstraint;
                    response.quantityConstraint = result.first;
                    response.numResults = result.second.size();

                    if (result.second.size() > nTopResult) {
                        result.second.subList(nTopResult, result.second.size()).clear();
                    }
                    response.topResults = result.second;
                    response.verdict = "OK";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response = new SearchResult();
            response.verdict = "Unknown error occured.";
        }
        return response;
    }
}
