package server.table.handler.search;

import model.quantity.QuantityConstraint;
import model.query.SimpleQueryParser;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONObject;
import pipeline.table.QfactTaxonomyGraph;
import server.common.handler.ResultCacheHandler;
import server.table.QfactLight;
import server.table.ResultInstance;
import server.table.TableQuery;
import storage.table.index.TableIndexStorage;
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

    public static final HashMap<Integer, QfactTaxonomyGraph.EntityTextQfact> BG_TEXT_QFACT_MAP = QfactTaxonomyGraph.loadBackgroundTextQfactMap();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        // Get parameters
        String typeConstraint = request.getParameter("type");
        String contextConstraint = request.getParameter("context");
        String quantityConstraint = request.getParameter("quantity");
        String fullConstraint = request.getParameter("full");

        Map additionalParams = new HashMap();
        if (request.getParameter("corpus") != null) {
            additionalParams.put("corpus", request.getParameter("corpus"));
        }

        if (request.getParameter("linking-threshold") != null) {
            additionalParams.put("linking-threshold", Float.parseFloat(request.getParameter("linking-threshold")));
        }

        String ntop = request.getParameter("ntop");
        int nResult = ntop != null ? Integer.parseInt(ntop) : 10;

        httpServletResponse.setCharacterEncoding("utf-8");

        String sessionKey = search(nResult, fullConstraint, typeConstraint, contextConstraint, quantityConstraint, additionalParams);

        httpServletResponse.getWriter().print(new JSONObject().put("s", sessionKey).toString());

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    // return session key
    public static String search(int nTopResult, String fullConstraint,
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
            if (fullConstraint != null) {
                Triple<String, String, String> parsed = SimpleQueryParser.parse(fullConstraint, SimpleQueryParser.SOURCE_CODE_TABLE);
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
                String suggestedType = SimpleQueryParser.suggestATypeFromRaw(typeConstraint, SimpleQueryParser.SOURCE_CODE_TABLE);
                if (suggestedType != null) {
                    typeConstraint = suggestedType;
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
                    response.fullQuery = fullConstraint;
                    response.typeConstraint = typeConstraint;
                    response.contextConstraint = contextConstraint;
                    response.quantityConstraint = result.first;
                    response.numResults = result.second.size();

                    if (result.second.size() > nTopResult) {
                        result.second.subList(nTopResult, result.second.size()).clear();
                    }
                    response.topResults = result.second;
                    response.verdict = "OK";

                    // group table indexes to reduce size
                    response.tableId2Index = new HashMap<>();
                    for (ResultInstance ri : response.topResults) {
                        for (ResultInstance.SubInstance si : ri.subInstances) {
                            if (!response.tableId2Index.containsKey(si.qfact.tableId)) {
                                response.tableId2Index.put(si.qfact.tableId, TableIndexStorage.get(si.qfact.tableId));
                            }
                            if (si.qfact.explainQfactIds != null && !si.qfact.explainQfactIds.equals("null")) {
                                si.qfact = (QfactLight) si.qfact.clone(); // IMPORTANT!

                                JSONArray bgQfactIds = new JSONArray(si.qfact.explainQfactIds);
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < bgQfactIds.length(); ++i) {
                                    if (sb.length() > 0) {
                                        sb.append("\r\n");
                                    }
                                    sb.append(BG_TEXT_QFACT_MAP.get(bgQfactIds.getInt(i)).toString());
                                }
                                si.qfact.explainStr = sb.toString();
                            }
                        }
                    }
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
