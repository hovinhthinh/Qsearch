package server.table.handler.search;

import model.context.ContextMatcher;
import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import model.query.SimpleQueryParser;
import nlp.NLP;
import server.table.explain.TypeLiftingRestrictor;
import server.text.ResultInstance;
import server.text.handler.search.SearchHandler;
import storage.text.migrate.ChronicleMapQuery;
import util.Gson;
import util.Pair;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OnlineExplanationHandler extends HttpServlet {
    public static final double EXACT_ENTITY_BOOST = 1.1;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        // Get parameters
        String typeConstraint = request.getParameter("type");
        String contextConstraint = request.getParameter("context");
        String alteredQuantityStr = request.getParameter("alteredQuantityStr");

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

        if ((v = request.getParameter("QUANTITY_MATCH_WEIGHT")) != null) {
            additionalParams.put("QUANTITY_MATCH_WEIGHT", Double.parseDouble(v));
        }

        if ((v = request.getParameter("ENTITY_POPULARITY_WEIGHT")) != null) {
            additionalParams.put("ENTITY_POPULARITY_WEIGHT", Double.parseDouble(v));
        }

        if ((v = request.getParameter("EXACT_ENTITY_BOOST")) != null) {
            additionalParams.put("EXACT_ENTITY_BOOST", Double.parseDouble(v));
        }

        String entity = request.getParameter("entity");
        if (entity == null) {
            throw new RuntimeException("Entity is null.");
        }

        if ((v = request.getParameter("TYPE_LIFTING_DIST_LIM")) != null) {
            additionalParams.put("TYPE_LIFTING_RESTRICTOR", new TypeLiftingRestrictor(entity, Integer.parseInt(v)));
        }

        int nResult = (v = request.getParameter("ntop")) != null ? Integer.parseInt(v) : 10;

        httpServletResponse.setCharacterEncoding("utf-8");

        server.text.handler.search.SearchResult result = search(null, nResult, typeConstraint, contextConstraint, alteredQuantityStr, entity, additionalParams);

        httpServletResponse.getWriter().print(Gson.toJson(result));

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static server.text.handler.search.SearchResult search(String model, int nTopResult,
                                                                 String typeConstraint, String contextConstraint, String alteredQuantityStr,
                                                                 String entity,
                                                                 Map additionalParameters) {
        // Optimize
        typeConstraint = NLP.stripSentence(typeConstraint);
        contextConstraint = NLP.stripSentence(contextConstraint);
        // handcraft quantity constraint
        QuantityConstraint qc = new QuantityConstraint();
        qc.quantity = Quantity.fromQuantityString(alteredQuantityStr);
        qc.resolutionCode = QuantityConstraint.QuantityResolution.Value.DOMAIN_ONLY; // important!
        qc.domain = QuantityDomain.getDomain(qc.quantity);
        qc.fineGrainedDomain = QuantityDomain.getFineGrainedDomain(qc.quantity);

        server.text.handler.search.SearchResult response = new server.text.handler.search.SearchResult();
        try {
            ContextMatcher matcher;
            if (model != null && model.equals(SearchHandler.KL_MODEL_STRING)) {
                matcher = SearchHandler.getKLMatcher();
            } else {
                model = SearchHandler.EMBEDDING_MODEL_STRING;
                matcher = ChronicleMapQuery.DEFAULT_MATCHER;
            }

            Pair<String, String> suggestedType = SimpleQueryParser.suggestATypeFromRaw(typeConstraint, SimpleQueryParser.SOURCE_CODE_TEXT);
            if (suggestedType != null) {
                typeConstraint = suggestedType.first;
            }

            Pair<QuantityConstraint, ArrayList<ResultInstance>> result =
                    ChronicleMapQuery.search(typeConstraint, contextConstraint, qc, matcher, additionalParameters);
            if (result.first == null) {
                response.verdict = "Cannot detect quantity constraint.";
            } else {
                response.matchingModel = model;
                response.typeConstraint = typeConstraint;
                response.contextConstraint = contextConstraint;
                response.quantityConstraint = result.first;
                response.numResults = result.second.size();

                response.topResults = result.second;
                response.verdict = "OK";

                // promote exact entity
                int pos = -1;
                for (int i = 0; i < response.topResults.size(); ++i) {
                    if (response.topResults.get(i).entity.equals(entity)) {
                        pos = i;
                        break;
                    }
                }
                if (pos >= nTopResult) {
                    response.topResults.set(nTopResult - 1, response.topResults.get(pos));
                    pos = nTopResult - 1;
                }

                // trim results
                if (response.topResults.size() > nTopResult) {
                    response.topResults.subList(nTopResult, response.topResults.size()).clear();
                }

                // boost exact entity
                if (pos != -1) {
                    double eeBoost = additionalParameters == null ? EXACT_ENTITY_BOOST :
                            (double) additionalParameters.getOrDefault("EXACT_ENTITY_BOOST", EXACT_ENTITY_BOOST);
                    response.topResults.get(pos).score /= eeBoost;
                    Collections.sort(response.topResults);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            response = new server.text.handler.search.SearchResult();
            response.verdict = "Unknown error occurred.";
        }
        return response;
    }
}
