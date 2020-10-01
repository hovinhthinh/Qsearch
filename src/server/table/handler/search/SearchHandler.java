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
import java.util.*;
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
        String v;
        if ((v = request.getParameter("corpus")) != null) {
            additionalParams.put("corpus", v);
        }

        if ((v = request.getParameter("linking-threshold")) != null) {
            additionalParams.put("linking-threshold", Double.parseDouble(v));
        }

        // locational params:
        if ((v = request.getParameter("HEADER_MATCH_WEIGHT")) != null) {
            additionalParams.put("HEADER_MATCH_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("CAPTION_MATCH_WEIGHT")) != null) {
            additionalParams.put("CAPTION_MATCH_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("TITLE_MATCH_WEIGHT")) != null) {
            additionalParams.put("TITLE_MATCH_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("SAME_ROW_MATCH_WEIGHT")) != null) {
            additionalParams.put("SAME_ROW_MATCH_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("RELATED_TEXT_MATCH_WEIGHT")) != null) {
            additionalParams.put("RELATED_TEXT_MATCH_WEIGHT", Double.parseDouble(v));
        }

        if ((v = request.getParameter("TOPIC_DRIFT_PENALTY")) != null) {
            additionalParams.put("TOPIC_DRIFT_PENALTY", Double.parseDouble(v));
        }

        if ((v = request.getParameter("QUANTITY_MATCH_WEIGHT")) != null) {
            additionalParams.put("QUANTITY_MATCH_WEIGHT", Double.parseDouble(v));
        }

        if ((v = request.getParameter("ENTITY_POPULARITY_WEIGHT")) != null) {
            additionalParams.put("ENTITY_POPULARITY_WEIGHT", Double.parseDouble(v));
        }

        // consistency params:
        if ((v = request.getParameter("HEADER_TF_WEIGHT")) != null) {
            additionalParams.put("HEADER_TF_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("CAPTION_TF_WEIGHT")) != null) {
            additionalParams.put("CAPTION_TF_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("TITLE_TF_WEIGHT")) != null) {
            additionalParams.put("TITLE_TF_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("SAME_ROW_TF_WEIGHT")) != null) {
            additionalParams.put("SAME_ROW_TF_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("RELATED_TEXT_TF_WEIGHT")) != null) {
            additionalParams.put("RELATED_TEXT_TF_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("QUANTITY_FEATURE_BOOST")) != null) {
            additionalParams.put("QUANTITY_FEATURE_BOOST", Double.parseDouble(v));
        }

        if ((v = request.getParameter("CONSISTENCY_LEARNING_N_FOLD")) != null) {
            additionalParams.put("CONSISTENCY_LEARNING_N_FOLD", Integer.parseInt(v));
        }
        if ((v = request.getParameter("CONSISTENCY_LEARNING_PROBE_RATE")) != null) {
            additionalParams.put("CONSISTENCY_LEARNING_PROBE_RATE", Double.parseDouble(v));
        }
        if ((v = request.getParameter("KNN_ESTIMATOR_K")) != null) {
            additionalParams.put("KNN_ESTIMATOR_K", Integer.parseInt(v));
        }
        if ((v = request.getParameter("INTERPOLATION_WEIGHT")) != null) {
            additionalParams.put("INTERPOLATION_WEIGHT", Double.parseDouble(v));
        }
        // end

        int nResult = (v = request.getParameter("ntop")) != null ? Integer.parseInt(v) : 10;

        httpServletResponse.setCharacterEncoding("utf-8");

        Set<String> groundtruth = null;
        try {
            groundtruth = new HashSet<>(Gson.fromJson(request.getParameter("groundtruth"), new ArrayList<String>().getClass()));
        } catch (Exception e) {
        }

        String sessionKey = search(nResult, fullConstraint, typeConstraint, contextConstraint, quantityConstraint,
                (v = request.getParameter("rescore")) != null && v.equals("true"), additionalParams, groundtruth);

        if ((v = request.getParameter("cache")) != null && v.equals("true")) {
            httpServletResponse.getWriter().print(new JSONObject().put("s", sessionKey).toString());
        } else {
            httpServletResponse.getWriter().print(ResultCacheHandler.getResultFromSession(sessionKey));
        }

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    // return session key
    public static String search(int nTopResult, String fullConstraint,
                                String typeConstraint, String contextConstraint, String quantityConstraint,
                                boolean performConsistencyRescoring,
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
                Pair<String, String> suggestedType = SimpleQueryParser.suggestATypeFromRaw(typeConstraint, SimpleQueryParser.SOURCE_CODE_TABLE);
                if (suggestedType != null) {
                    typeConstraint = suggestedType.first;
                }
            }
            if (response.verdict == null) {
                Pair<QuantityConstraint, ArrayList<ResultInstance>> result =
                        TableQuery.search(typeConstraint, contextConstraint, quantityConstraint, performConsistencyRescoring, additionalParameters);
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

                    // Compute recall-based metrics from groundtruth
                    if (groundtruth != null) {
                        response.AP = 0.0;
                        response.RR = 0.0;
                        response.RECALL = 0.0;
                        response.RECALL_10 = 0.0;
                        int nTrue = 0;
                        for (int i = 0; i < result.second.size(); ++i) {
                            ResultInstance ri = result.second.get(i);
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
