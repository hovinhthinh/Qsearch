package server.table.handler.search;

import model.context.ContextMatcher;
import model.context.KullBackLeiblerMatcher;
import model.quantity.QuantityConstraint;
import model.query.SimpleQueryParser;
import nlp.NLP;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONException;
import storage.table.ElasticSearchTableQuery;
import storage.table.ResultInstance;
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
    public static final String KL_MODEL_STRING = "KL";
    public static final String EMBEDDING_MODEL_STRING = "EMBEDDING";

    private static ContextMatcher KULLBACK_LEIBLER_MATCHER = null;

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
//        if (request.getParameter("corpus") != null) {
//            additionalParams.put("corpus", request.getParameter("corpus")); // ANY || STICS || NYT
//        }
        if (request.getParameter("model") != null) {
            additionalParams.put("model", request.getParameter("model")); // EMBEDDING || KL
        }
        if (request.getParameter("alpha") != null) {
            additionalParams.put("alpha", Float.parseFloat(request.getParameter("alpha")));
        }
        if (request.getParameter("lambda") != null) {
            additionalParams.put("lambda", Float.parseFloat(request.getParameter("lambda")));
        }

        String ntop = request.getParameter("ntop");
        int nResult = ntop != null ? Integer.parseInt(ntop) : nTopResult;

        String ntable = request.getParameter("ntable");
        int nTopTable = ntable != null ? Integer.parseInt(ntable) : 1000;

        String linkingThreshold = request.getParameter("link_threshold");
        double linkThreshold = linkingThreshold != null ? Double.parseDouble(linkingThreshold) : -1;

        httpServletResponse.setCharacterEncoding("utf-8");

        SearchResult response = search(null, nResult, nTopTable, linkThreshold, fullConstraint, typeConstraint, contextConstraint, quantityConstraint, additionalParams);

        httpServletResponse.getWriter().print(Gson.toJson(response));

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static SearchResult search(String model, int nTopResult, int nTopTable, double linkingThreshold, String fullConstraint,
                                      String typeConstraint, String contextConstraint, String quantityConstraint, Map additionalParameters) {
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
            String explicitMatchingModel = additionalParameters == null ? null : (String) additionalParameters.get("model");
            if (explicitMatchingModel != null) {
                model = explicitMatchingModel;
            }
            ContextMatcher matcher;
            if (model != null && model.equals(KL_MODEL_STRING)) {
                if (KULLBACK_LEIBLER_MATCHER == null) {
                    KULLBACK_LEIBLER_MATCHER = new KullBackLeiblerMatcher(0.1);
                }
                matcher = KULLBACK_LEIBLER_MATCHER;
            } else {
                model = EMBEDDING_MODEL_STRING;
                matcher = ElasticSearchTableQuery.DEFAULT_MATCHER;
            }
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
                        ElasticSearchTableQuery.search(fullConstraint, nTopTable, linkingThreshold,
                                typeConstraint, contextConstraint, quantityConstraint, nTopResult, matcher, additionalParameters);
                if (result.first == null) {
                    response.verdict = "Cannot detect quantity constraint.";
                } else if (result.second == null) {
                    response.verdict = "Elasticsearch error.";
                } else {
                    response.matchingModel = model;
                    response.typeConstraint = typeConstraint;
                    response.contextConstraint = contextConstraint;
                    response.quantityConstraint = result.first;
                    response.numResults = result.second.size();

                    int nPrintedTopResult = nTopResult;
                    for (ResultInstance o : result.second) {
                        try {
                            if (nPrintedTopResult-- > 0) {
                                response.topResults.add(o);
                            } else {
                                break;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
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
