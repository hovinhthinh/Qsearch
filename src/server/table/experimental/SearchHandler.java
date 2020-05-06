package server.table.experimental;

import com.google.gson.Gson;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import model.context.ContextMatcher;
import model.context.IDF;
import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import nlp.Glove;
import nlp.NLP;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import uk.ac.susx.informatics.Morpha;
import util.headword.StringUtils;
import yago.TaxonomyGraph;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

@Deprecated
public class SearchHandler extends AbstractHandler {
    public static final Logger LOGGER = Logger.getLogger(SearchHandler.class.getName());
    private static Gson GSON = new Gson();

    static ArrayList<Qfact> qfacts = TableQfactSaver.load();

    public static ContextMatcher DEFAULT_MATCHER = new ContextMatcher() {
        // Higher is better
        // range from 0 -> 1
        public double directedEmbeddingIdfSimilarity(ArrayList<String> queryX, ArrayList<String> factX) {
            // TODO: Currently not supporting TIME (TIME is computed like normal terms).
            if (queryX.isEmpty() || factX.isEmpty()) {
                return queryX.isEmpty() && factX.isEmpty() ? 1 : 0;
            }
            double score = 0;
            double totalIdf = 0;
            for (String qX : queryX) {
                double max = 0;
                for (String fX : factX) {
                    double sim = Glove.cosineDistance(qX, fX);
                    if (sim != -1) {
                        max = Math.max(max, 1 - sim);
                    }
                }
                double idf = IDF.getRobertsonIdf(qX);
                score += max * idf;
                totalIdf += idf;
            }
            return score / totalIdf;
        }

        @Override
        public double match(ArrayList<String> queryContext, ArrayList<String> factContext) {
            return directedEmbeddingIdfSimilarity(queryContext, factContext);
        }
    };


    private int nTopResult;

    public SearchHandler(int nTopResult) {
        this.nTopResult = nTopResult;
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException {
        request.setHandled(true);

        // Get parameters
        String typeConstraint = request.getParameter("type");
        String contextConstraint = request.getParameter("context");
        String quantityConstraint = request.getParameter("quantity");

        Map additionalParams = new HashMap();

        String ntop = request.getParameter("ntop");
        int nResult = ntop != null ? Integer.parseInt(ntop) : nTopResult;

        httpServletResponse.setCharacterEncoding("utf-8");

        ArrayList<ResultInstance> response = search(nResult, typeConstraint, contextConstraint, quantityConstraint, additionalParams);

        synchronized (GSON) {
            httpServletResponse.getWriter().print(GSON.toJson(response));
        }
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static ArrayList<ResultInstance> search(int nTopResult, String queryType, String queryContext, String quantityConstraint, Map additionalParameters) {
        // Optimize
        if (queryType != null) {
            queryType = NLP.stripSentence(queryType).toLowerCase();
        }
        if (queryContext != null) {
            queryContext = NLP.stripSentence(queryContext).toLowerCase();
        }

        QuantityConstraint constraint = null;
        if (quantityConstraint != null) {
            constraint = QuantityConstraint.parseFromString(quantityConstraint.toLowerCase());
            if (constraint == null) {
                return null;
            }
            if (QuantityDomain.getDomain(constraint.quantity) == QuantityDomain.Domain.DIMENSIONLESS) {
                queryContext += " " + constraint.quantity.unit;
                constraint.quantity.unit = "";
            }
        }

        queryType = queryType.toLowerCase();
        queryContext = queryContext.toLowerCase();

        // Process query context terms
        ArrayList<String> queryContextTerms = NLP.splitSentence(NLP.fastStemming(queryContext.toLowerCase(), Morpha.any));

        ArrayList<ResultInstance> response = new ArrayList<>();
        if (!queryType.isEmpty()) {
            String optimizedQueryType = NLP.stripSentence(NLP.fastStemming(queryType.toLowerCase(), Morpha.noun));
            String searchingHead = NLP.getHeadWord(optimizedQueryType, true);

            for (int i = 0; i < qfacts.size(); ++i) {
                if (i > 0 && qfacts.get(i).entity.equals(qfacts.get(i - 1).entity)) {
                    continue;
                }
                String entity = qfacts.get(i).entity;

                int j = i;
                while (j < qfacts.size() - 1 && qfacts.get(j + 1).entity.equals(entity)) {
                    ++j;
                }
                // process type
                Int2IntLinkedOpenHashMap typeSet = TaxonomyGraph.getDefaultGraphInstance().getType2DistanceMapForEntity(
                        TaxonomyGraph.getDefaultGraphInstance().entity2Id.get("<" + entity.substring(5) + ">")
                );
                boolean flag = false;
                for (int typeId : typeSet.keySet()) {
                    String typeStr = TaxonomyGraph.getDefaultGraphInstance().id2TextualizedType.get(typeId);
                    if (typeStr.contains(optimizedQueryType) && searchingHead.equals(NLP.getHeadWord(typeStr, true))) {
                        flag = true;
                        break;
                    }
                }
                if (!flag) {
                    i = j;
                    continue;
                }

                ResultInstance inst = new ResultInstance();
                inst.entity = "<" + entity.substring(5) + ">";

                for (int k = i; k <= j; ++k) {
                    Qfact qfact = qfacts.get(k);
                    // quantity
                    Quantity qt = Quantity.fromQuantityString(qfact.quantity);
                    if (constraint != null && !constraint.match(qt)) {
                        continue;
                    }
                    // context
                    ArrayList<String> X = new ArrayList<>(Arrays.asList(qfact.context.split(" ")));

                    ResultInstance.SubInstance si = new ResultInstance.SubInstance();
                    si.quantity = qt.toString(2);
                    si.context = qfact.context;
                    si.domain = QuantityDomain.getDomain(qt, true);
                    si.source = qfact.source;

                    // match
                    for (int l = 0; l < X.size(); ++l) {
                        X.set(l, StringUtils.stem(X.get(l).toLowerCase(), Morpha.any));
                    }
                    // use explicit matcher if given.
                    si.score = DEFAULT_MATCHER.match(queryContextTerms, X);
                    if (si.score < 0.7) {
                        continue;
                    }
                    inst.addSubInstance(si);
                }

                if (inst.subInstances.size() > 0) {
                    Collections.sort(inst.subInstances, (o1, o2) -> Double.compare(o2.score, o1.score));
                    response.add(inst);
                }

                // done processing
                i = j;
            }
        }
        Collections.sort(response, (o1, o2) -> Double.compare(o2.score, o1.score));

        LOGGER.info("Query: {Type: \"" + queryType + "\"; Context: \"" + queryContext +
                "\"; Quantity: \"" + quantityConstraint + "\"}");

        if (response.size() > nTopResult) {
            response.subList(nTopResult, response.size()).clear();
        }
        return response;
    }
}
