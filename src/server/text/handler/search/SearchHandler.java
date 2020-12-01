package server.text.handler.search;

import model.context.ContextMatcher;
import model.context.KullBackLeiblerMatcher;
import model.quantity.QuantityConstraint;
import model.query.SimpleQueryParser;
import nlp.NLP;
import org.json.JSONObject;
import server.common.handler.ResultCacheHandler;
import storage.text.ElasticSearchQuery;
import util.Gson;
import util.Pair;
import util.Triple;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class SearchHandler extends HttpServlet {
    public static final Logger LOGGER = Logger.getLogger(SearchHandler.class.getName());
    public static final String KL_MODEL_STRING = "KL";
    public static final String EMBEDDING_MODEL_STRING = "EMBEDDING";

    private static ContextMatcher KULLBACK_LEIBLER_MATCHER = null;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        // Get parameters
        String typeConstraint = request.getParameter("type");
        String contextConstraint = request.getParameter("context");
        String quantityConstraint = request.getParameter("quantity");
        String fullConstraint = request.getParameter("full");

        String v;
        Map additionalParams = new HashMap();
        if ((v = request.getParameter("corpus")) != null) {
            additionalParams.put("corpus", v); // ANY || STICS || NYT
        }
        if ((v = request.getParameter("model")) != null) {
            additionalParams.put("model", v); // EMBEDDING || KL
        }
        if ((v = request.getParameter("alpha")) != null) {
            additionalParams.put("alpha", Double.parseDouble(v));
        }
        if ((v = request.getParameter("lambda")) != null) {
            additionalParams.put("lambda", Double.parseDouble(v));
        }

        int nResult = (v = request.getParameter("ntop")) != null ? Integer.parseInt(v) : 10;

        httpServletResponse.setCharacterEncoding("utf-8");

        Set<String> groundtruth = null;
        try {
            groundtruth = new HashSet<>(Gson.fromJson(request.getParameter("groundtruth"), new ArrayList<String>().getClass()));
        } catch (Exception e) {
        }

        if ((v = request.getParameter("nResultsPerPage")) != null) {
            additionalParams.put("nResultsPerPage", Integer.parseInt(v));
        }

        String sessionKey = search(null, nResult, fullConstraint, typeConstraint, contextConstraint, quantityConstraint, additionalParams, groundtruth);

        if ((v = request.getParameter("cache")) != null && v.equals("true")) {
            httpServletResponse.getWriter().print(new JSONObject().put("s", sessionKey).toString());
        } else {
            httpServletResponse.getWriter().print(ResultCacheHandler.getResultFromSession(sessionKey));
        }

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static String search(String model, int nTopResult, String fullConstraint,
                                String typeConstraint, String contextConstraint, String quantityConstraint,
                                Map additionalParameters, Set<String> groundtruth) {
        // Optimize
        if (typeConstraint != null) {
            typeConstraint = NLP.stripSentence(typeConstraint);
        }
        if (contextConstraint != null) {
            contextConstraint = NLP.stripSentence(contextConstraint);
        }
        if (quantityConstraint != null) {
            quantityConstraint = NLP.stripSentence(quantityConstraint);
        }
        if (fullConstraint != null) {
            fullConstraint = NLP.stripSentence(fullConstraint);
        }

        if (fullConstraint == null) {
            LOGGER.info("Query: {Type: \"" + typeConstraint + "\"; Context: \"" + contextConstraint +
                    "\"; Quantity: \"" + quantityConstraint + "\"}");
        } else {
            LOGGER.info("Query: {Full: \"" + fullConstraint + "\"}");
        }
        SearchResult response = new SearchResult();
        try {
            ContextMatcher matcher;
            if (model != null && model.equals(KL_MODEL_STRING)) {
                if (KULLBACK_LEIBLER_MATCHER == null) {
                    KULLBACK_LEIBLER_MATCHER = new KullBackLeiblerMatcher(0.1);
                }
                matcher = KULLBACK_LEIBLER_MATCHER;
            } else {
                model = EMBEDDING_MODEL_STRING;
                matcher = ElasticSearchQuery.DEFAULT_MATCHER;
            }
            if (fullConstraint != null) {
                Triple<String, String, String> parsed = SimpleQueryParser.parse(fullConstraint, SimpleQueryParser.SOURCE_CODE_TEXT);
                if (parsed == null) {
                    response.verdict = "Cannot parse full query.";
                } else {
                    typeConstraint = parsed.first;
                    contextConstraint = parsed.second;
                    quantityConstraint = parsed.third;
                    LOGGER.info("Parsed query: {Type: \"" + typeConstraint + "\"; Context: \"" + contextConstraint +
                            "\"; Quantity: \"" + quantityConstraint + "\"}");
                }
            } else if (typeConstraint != null) {
                Pair<String, String> suggestedType = SimpleQueryParser.suggestATypeFromRaw(typeConstraint, SimpleQueryParser.SOURCE_CODE_TEXT);
                if (suggestedType != null) {
                    typeConstraint = suggestedType.first;
                }
            }
            if (response.verdict == null) {
                Pair<QuantityConstraint, ArrayList<SearchResult.ResultInstance>> result =
                        ElasticSearchQuery.search(typeConstraint, contextConstraint, quantityConstraint, matcher, additionalParameters);
                if (result.first == null) {
                    response.verdict = "Cannot detect quantity constraint.";
                } else if (result.second == null) {
                    response.verdict = "Elasticsearch error.";
                } else {
                    response.matchingModel = model;
                    response.fullQuery = fullConstraint;
                    response.typeConstraint = typeConstraint;
                    response.contextConstraint = contextConstraint;
                    response.quantityConstraint = result.first;
                    response.numResults = result.second.size();

                    // Compute recall-based metrics from groundtruth
                    if (groundtruth != null) {
                        response.AP = 0.0;
                        response.RR = 0.0;
                        response.RECALL = 0.0;
                        response.RECALL_10 = 0.0;
                        int nTrue = 0;
                        for (int i = 0; i < result.second.size(); ++i) {
                            SearchResult.ResultInstance ri = result.second.get(i);
                            if (i < nTopResult) {
                                ri.eval = "false";
                            }
                            if (groundtruth.contains(ri.entity)) {
                                if (i < nTopResult) {
                                    ri.eval = "true";
                                }
                                if (response.RR == 0.0) {
                                    response.RR = ((double) 1) / (i + 1);
                                }
                                ++nTrue;
                                response.AP += ((double) nTrue) / (i + 1);
                                if (i < groundtruth.size()) {
                                    response.RECALL += 1;
                                }
                                if (i < 10) {
                                    response.RECALL_10 += 1;
                                }
                            }
                        }
                        if (groundtruth.size() > 0) {
                            response.AP /= groundtruth.size();
                            response.RECALL /= groundtruth.size();
                            response.RECALL_10 /= groundtruth.size();
                        }
                    }

                    response.topResults = result.second;
                    response.verdict = "OK";

                    if (nTopResult == -1) { // enable pagination
                        response.nResultsPerPage = (int) additionalParameters.getOrDefault("nResultsPerPage", 20);
                        response.nPage = (response.topResults.size() - 1) / response.nResultsPerPage + 1;
                    } else {
                        if (response.topResults.size() > nTopResult) {
                            response.topResults.subList(nTopResult, response.topResults.size()).clear();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response = new SearchResult();
            response.verdict = "Unknown error occurred.";
        }
        return ResultCacheHandler.addResult(response.verdict.equals("OK") && nTopResult == -1 ? response : Gson.toJson(response));
    }
}
