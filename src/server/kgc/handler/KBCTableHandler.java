package server.kgc.handler;

import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import model.quantity.kg.KgUnit;
import nlp.NLP;
import server.table.ResultInstance;
import server.table.TableQuery;
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

public class KBCTableHandler extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        // Get parameters
        String typeConstraint = request.getParameter("type");
        String contextConstraint = request.getParameter("context");
        String quantitySearchDomain = request.getParameter("quantitySearchDomain");
        String quantitySiUnit = request.getParameter("quantitySiUnit");

        Map additionalParams = new HashMap();
        String v;
        if ((v = request.getParameter("corpus")) != null) {
            additionalParams.put("corpus", v);
        }

        if ((v = request.getParameter("linking-threshold")) != null) {
            additionalParams.put("linking-threshold", Double.parseDouble(v));
        }

        if ((v = request.getParameter("n-evidence")) != null) {
            additionalParams.put("n-evidence", Integer.parseInt(v));
        }

        if ((v = request.getParameter("conf-threshold")) != null) {
            additionalParams.put("conf-threshold", Double.parseDouble(v));
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
        if ((v = request.getParameter("DOM_HEADING_MATCH_WEIGHT")) != null) {
            additionalParams.put("DOM_HEADING_MATCH_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("SAME_ROW_MATCH_WEIGHT")) != null) {
            additionalParams.put("SAME_ROW_MATCH_WEIGHT", Double.parseDouble(v));
        }
        if ((v = request.getParameter("RELATED_TEXT_MATCH_WEIGHT")) != null) {
            additionalParams.put("RELATED_TEXT_MATCH_WEIGHT", Double.parseDouble(v));
        }

        if ((v = request.getParameter("TYPE_IDF_SCALE")) != null) {
            additionalParams.put("TYPE_IDF_SCALE", Double.parseDouble(v));
        }

        if ((v = request.getParameter("TOPIC_DRIFT_PENALTY")) != null) {
            additionalParams.put("TOPIC_DRIFT_PENALTY", Double.parseDouble(v));
        }

        additionalParams.put("QUANTITY_MATCH_WEIGHT", 0.0);
        additionalParams.put("ENTITY_POPULARITY_WEIGHT", 0.0);

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
        if ((v = request.getParameter("DOM_HEADING_TF_WEIGHT")) != null) {
            additionalParams.put("DOM_HEADING_TF_WEIGHT", Double.parseDouble(v));
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

        server.table.handler.search.SearchResult result = search(nResult, typeConstraint, contextConstraint, quantitySearchDomain, quantitySiUnit,
                (v = request.getParameter("rescore")) != null && v.equals("true"), additionalParams);

        httpServletResponse.getWriter().print(Gson.toJson(result));

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static server.table.handler.search.SearchResult search(int nTopResult,
                                                                  String typeConstraint,
                                                                  String contextConstraint,
                                                                  String quantitySearchDomain,
                                                                  String quantitySiUnit,
                                                                  boolean performConsistencyRescoring,
                                                                  Map additionalParameters) {
        // Optimize
        typeConstraint = NLP.stripSentence(typeConstraint);
        contextConstraint = NLP.stripSentence(contextConstraint);

        server.table.handler.search.SearchResult response = new server.table.handler.search.SearchResult();

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
            Pair<QuantityConstraint, ArrayList<ResultInstance>> result =
                    TableQuery.search(typeConstraint, contextConstraint, qc, performConsistencyRescoring, additionalParameters);

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
            response.populateTableIndexesFromTopResults();

            // drop unnecessary info
            response.tableId2Index = null;
            for (ResultInstance ri : response.topResults) {
                for (ResultInstance.SubInstance si : ri.subInstances) {
                    si.traces = null;
                    si.quantityConvertedStr = null;
                    si.qfact.explainQfactIds = null;
                    si.qfact.explainStr = null;
                    si.qfact.entitySpan = null;
                    si.qfact.entity = null;
                    si.qfact.headerContext = null;
                    si.qfact.headerUnitSpan = null;
                    si.qfact.quantitySpan = null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response = new server.table.handler.search.SearchResult();
            response.verdict = "Unknown error occurred.";
        }
        return response;
    }
}
