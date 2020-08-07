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

        Map additionalParams = new HashMap();
        if (request.getParameter("corpus") != null) {
            additionalParams.put("corpus", request.getParameter("corpus")); // ANY || STICS || NYT
        }
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
        int nResult = ntop != null ? Integer.parseInt(ntop) : 10;

        httpServletResponse.setCharacterEncoding("utf-8");

        Set<String> groundtruth = null;
        try {
            String gt = request.getParameter("groundtruth");
            groundtruth = new HashSet<>(Gson.fromJson(gt, new ArrayList<String>().getClass()));
        } catch (Exception e) {
        }

        String sessionKey = search(null, nResult, fullConstraint, typeConstraint, contextConstraint, quantityConstraint, additionalParams, groundtruth);

        httpServletResponse.getWriter().print(new JSONObject().put("s", sessionKey).toString());

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
                String suggestedType = SimpleQueryParser.suggestATypeFromRaw(typeConstraint, SimpleQueryParser.SOURCE_CODE_TEXT);
                if (suggestedType != null) {
                    typeConstraint = suggestedType;
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
                                response.AP += (nTrue) / (i + 1);
                                if (i < groundtruth.size()) {
                                    response.RECALL += 1;
                                }
                            }
                        }
                        if (groundtruth.size() > 0) {
                            response.AP /= groundtruth.size();
                            response.RECALL /= groundtruth.size();
                        }
                    }

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
            response.verdict = "Unknown error occurred.";
        }
        return ResultCacheHandler.addResult(Gson.toJson(response));
    }
}
