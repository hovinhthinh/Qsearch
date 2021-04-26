package server.kgc.handler;

import model.context.ContextMatcher;
import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import model.quantity.kg.KgUnit;
import nlp.NLP;
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
import java.util.HashMap;
import java.util.Map;

public class KBCTextHandler extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        // Get parameters
        String typeConstraint = request.getParameter("type");
        String contextConstraint = request.getParameter("context");
        String quantitySearchDomain = request.getParameter("quantitySearchDomain");
        String quantitySiUnit = request.getParameter("quantitySiUnit");

        String v;
        Map additionalParams = new HashMap();
        if ((v = request.getParameter("corpus")) != null) {
            additionalParams.put("corpus", v);
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
        if ((v = request.getParameter("n-evidence")) != null) {
            additionalParams.put("n-evidence", Integer.parseInt(v));
        }
        if ((v = request.getParameter("matching-conf-threshold")) != null) {
            additionalParams.put("matching-conf-threshold", Double.parseDouble(v));
        }

        additionalParams.put("QUANTITY_MATCH_WEIGHT", 0.0);
        additionalParams.put("ENTITY_POPULARITY_WEIGHT", 0.0);

        int nResult = (v = request.getParameter("ntop")) != null ? Integer.parseInt(v) : 10;

        httpServletResponse.setCharacterEncoding("utf-8");

        server.text.handler.search.SearchResult result = search(null, nResult, typeConstraint, contextConstraint, quantitySearchDomain, quantitySiUnit, additionalParams);

        httpServletResponse.getWriter().print(Gson.toJson(result));

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static server.text.handler.search.SearchResult search(String model, int nTopResult,
                                                                 String typeConstraint,
                                                                 String contextConstraint,
                                                                 String quantitySearchDomain,
                                                                 String quantitySiUnit,
                                                                 Map additionalParameters) {
        // Optimize
        typeConstraint = NLP.stripSentence(typeConstraint);
        contextConstraint = NLP.stripSentence(contextConstraint);

        server.text.handler.search.SearchResult response = new server.text.handler.search.SearchResult();

        // handcraft quantity constraint
        QuantityConstraint qc = new QuantityConstraint();
        qc.quantity = new Quantity(1.0, "", "=");
        qc.resolutionCode = QuantityConstraint.QuantityResolution.Value.DOMAIN_ONLY; // important!

        if (quantitySiUnit != null) {
            KgUnit kgu = KgUnit.getKgUnitFromEntityName(quantitySiUnit);
            if (kgu == null) {
                response.verdict = "Cannot find quantity domain from unit.";
                return response;
            }
            String siDomain = kgu.getSIDomain();
            quantitySearchDomain = (kgu.conversionToSI != null
                    && (!QuantityDomain.USE_NARROW_SEARCH_DOMAINS || QuantityDomain.Domain.NARROW_SEARCH_DOMAINS.contains(siDomain))
                    ? siDomain : QuantityDomain.Domain.DIMENSIONLESS);
        }
        qc.searchDomain = quantitySearchDomain;

        try {
            ContextMatcher matcher;
            if (model != null && model.equals(SearchHandler.KL_MODEL_STRING)) {
                matcher = SearchHandler.getKLMatcher();
            } else {
                model = SearchHandler.EMBEDDING_MODEL_STRING;
                matcher = ChronicleMapQuery.DEFAULT_MATCHER;
            }

            Pair<QuantityConstraint, ArrayList<ResultInstance>> result =
                    ChronicleMapQuery.search(typeConstraint, contextConstraint, qc, matcher, additionalParameters);

            response.matchingModel = model;
            response.typeConstraint = typeConstraint;
            response.contextConstraint = contextConstraint;
            response.quantityConstraint = result.first;
            response.numResults = result.second.size();

            response.topResults = result.second;
            response.verdict = "OK";

            // trim results
            if (response.topResults.size() > nTopResult) {
                response.topResults.subList(nTopResult, response.topResults.size()).clear();
            }

            // drop unnecessary info
            for (ResultInstance ri : response.topResults) {
                for (ResultInstance.SubInstance si : ri.subInstances) {
                    si.entityStr = null;
                    si.quantityStr = null;
                    si.contextStr = null;
                    si.quantityConvertedStr = null;
                    si.sentence = null;
                    si.source = null;
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
