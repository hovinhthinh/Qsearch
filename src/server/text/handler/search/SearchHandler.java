package server.text.handler.search;

import model.context.ContextMatcher;
import model.context.KullBackLeiblerMatcher;
import model.quantity.QuantityConstraint;
import model.query.SimpleQueryParser;
import nlp.NLP;
import org.json.JSONException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
        String sessionKey = search(null, nResult, fullConstraint, typeConstraint, contextConstraint, quantityConstraint, additionalParams);

        httpServletResponse.getWriter().print(new JSONObject().put("s", sessionKey).toString());

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static String search(String model, int nTopResult, String fullConstraint,
                                String typeConstraint, String contextConstraint, String quantityConstraint,
                                Map additionalParameters) {
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
                Pair<QuantityConstraint, ArrayList<JSONObject>> result =
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

                    int nPrintedTopResult = nTopResult;
                    for (JSONObject o : result.second) {
                        try {
                            if (nPrintedTopResult-- > 0) {
                                SearchResult.ResultInstance instance = new SearchResult.ResultInstance();
                                instance.entity = o.getString("_id");
                                instance.score = o.getDouble("match_score");
                                instance.quantity = o.getString("match_quantity");
                                instance.quantityStandardValue = o.getDouble("match_quantity_standard_value");
                                instance.sentence = o.getString("match_sentence");
                                instance.source = o.getString("match_source");

                                instance.entityStr = o.getString("match_entity_str");
                                instance.quantityStr = o.getString("match_quantity_str");

                                instance.quantityConvertedStr = o.getString("match_quantity_converted_str");

                                instance.contextStr = Gson.fromJson(o.getString("match_context_str"), new ArrayList<String>().getClass());

                                response.topResults.add(instance);
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
            response.verdict = "Unknown error occurred.";
        }
        return ResultCacheHandler.addResult(Gson.toJson(response));
    }
}
