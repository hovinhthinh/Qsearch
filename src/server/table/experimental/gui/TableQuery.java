package server.table.experimental.gui;

import model.context.IDF;
import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import nlp.Glove;
import nlp.NLP;
import org.eclipse.jetty.websocket.api.Session;
import server.table.experimental.QfactLight;
import server.table.experimental.TableQfactLoader;
import uk.ac.susx.informatics.Morpha;
import util.Gson;
import util.Pair;
import util.headword.StringUtils;
import yago.TaxonomyGraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;


public class TableQuery {
    public static final Logger LOGGER = Logger.getLogger(TableQuery.class.getName());
    public static double HEADER_MATCH_WEIGHT = 1;
    public static double CAPTION_MATCH_WEIGHT = 0.9;
    public static double TITLE_MATCH_WEIGHT = 0.9;

    private static ArrayList<QfactLight> QFACTS = TableQfactLoader.load();
    private static TaxonomyGraph TAXONOMY = TaxonomyGraph.getDefaultGraphInstance();

    public static Pair<Double, ArrayList<ResultInstance.SubInstance.ContextMatchTrace>> match(ArrayList<String> queryX, QfactLight f) {
        double score = 0;
        ArrayList<ResultInstance.SubInstance.ContextMatchTrace> traces = new ArrayList<>();
        double totalIdf = 0;
        for (String qX : queryX) {
            ResultInstance.SubInstance.ContextMatchTrace trace = new ResultInstance.SubInstance.ContextMatchTrace(null, 0, null);
            // HEADER
            for (String fX : NLP.splitSentence(f.headerContext.toLowerCase())) {
                if (NLP.BLOCKED_STOPWORDS.contains(fX)) {
                    continue;
                }
                double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                if (sim == -1) {
                    continue;
                }
                sim = (1 - sim) * HEADER_MATCH_WEIGHT;

                if (trace.score < sim) {
                    trace.score = sim;
                    trace.token = fX;
                    trace.place = "HEADER";
                }
            }
            // CAPTION
            for (String fX : NLP.splitSentence(f.tableIndex.caption.toLowerCase())) {
                if (NLP.BLOCKED_STOPWORDS.contains(fX)) {
                    continue;
                }
                double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                if (sim == -1) {
                    continue;
                }
                sim = (1 - sim) * CAPTION_MATCH_WEIGHT;

                if (trace.score < sim) {
                    trace.score = sim;
                    trace.token = fX;
                    trace.place = "CAPTION";
                }
            }
            // TITLE
            for (String fX : NLP.splitSentence(f.tableIndex.pageTitle.toLowerCase())) {
                if (NLP.BLOCKED_STOPWORDS.contains(fX)) {
                    continue;
                }
                double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                if (sim == -1) {
                    continue;
                }
                sim = (1 - sim) * TITLE_MATCH_WEIGHT;

                if (trace.score < sim) {
                    trace.score = sim;
                    trace.token = fX;
                    trace.place = "TITLE";
                }
            }

            if (trace.score > 0) {
                boolean toBeAdded = true;
                for (ResultInstance.SubInstance.ContextMatchTrace t : traces) {
                    if (t.token.equals(trace.token) && t.place.equals(trace.place) && t.score > trace.score) {
                        toBeAdded = false;
                        break;
                    }
                }
                if (toBeAdded) {
                    traces.add(trace);
                }
            }

            double idf = IDF.getRobertsonIdf(qX);
            score += trace.score * idf;
            totalIdf += idf;
        }
        if (totalIdf > 0) {
            score /= totalIdf;
        }

        return new Pair<>(score, traces);
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext, String quantityConstraint,
                                                                             Map additionalParameters) {
        quantityConstraint = quantityConstraint.toLowerCase();

        Pair<QuantityConstraint, ArrayList<ResultInstance>> result = new Pair<>();

        QuantityConstraint constraint = QuantityConstraint.parseFromString(quantityConstraint);
        result.first = constraint;
        if (constraint == null) {
            return result;
        }

        // Build query head word and query type set
        queryType = NLP.stripSentence(NLP.fastStemming(queryType.toLowerCase(), Morpha.noun));
        String queryHeadWord = NLP.getHeadWord(queryType, true);

        // Process query context terms
        if (QuantityDomain.getDomain(constraint.quantity).equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            queryContext += " " + constraint.quantity.unit;
        }
        ArrayList<String> queryContextTerms = NLP.splitSentence(NLP.fastStemming(queryContext.toLowerCase(), Morpha.any));


        // Corpus constraint
        ArrayList<String> corpusConstraint = new ArrayList<>();
        corpusConstraint = additionalParameters == null ? null :
                (additionalParameters.containsKey("corpus") ? Gson.fromJson((String) additionalParameters.get("corpus"), corpusConstraint.getClass()) : null);

        // retrieve additional parameters
        Session session = additionalParameters == null ? null : (Session) additionalParameters.get("session");
        int lastPercent = 0;

        result.second = new ArrayList<>();
        for (int i = 0; i < QFACTS.size(); ++i) {
            // log progress
            if (session != null) {
                int currentPercent = (int) ((double) (i + 1) * 100 / QFACTS.size());
                if (currentPercent > lastPercent) {
                    lastPercent = currentPercent;
                    try {
                        session.getRemote().sendString("{\"progress\":" + currentPercent + "}");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (i > 0 && QFACTS.get(i).entity.equals(QFACTS.get(i - 1).entity)) {
                continue;
            }
            String entity = QFACTS.get(i).entity;

            int j = i;
            while (j < QFACTS.size() - 1 && QFACTS.get(j + 1).entity.equals(entity)) {
                ++j;
            }
            // process type
            boolean flag = false;
            for (String type : TAXONOMY.getTextualizedTypes("<" + entity.substring(5) + ">", true)) {
                if (type.contains(queryType) && queryHeadWord.equals(NLP.getHeadWord(type, true))) {
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
                QfactLight f = QFACTS.get(k);
                // check corpus target
                if (corpusConstraint != null) {
                    boolean goodSource = false;
                    for (String c : corpusConstraint) {
                        if (f.tableIndex.table.source.startsWith(c + ":")) {
                            goodSource = true;
                            break;
                        }
                    }
                    if (!goodSource) {
                        continue;
                    }
                }

                // quantity
                Quantity qt = Quantity.fromQuantityString(f.quantity);
                if (!constraint.match(qt)) {
                    continue;
                }

                Pair<Double, ArrayList<ResultInstance.SubInstance.ContextMatchTrace>> matchScore = match(queryContextTerms, f);

                if (matchScore.first < 0.7) {
                    continue;
                }
                //
                double quantityStandardValue = qt.value * QuantityDomain.getScale(qt);
                // quantity convert str
                String matchQuantityConvertedStr = null;
                double scale = QuantityDomain.getScale(qt) / QuantityDomain.getScale(constraint.quantity);
                if (Math.abs(scale - 1.0) >= 1e-6) {
                    double convertedValue = scale * qt.value;
                    if (Math.abs(convertedValue) >= 1e9) {
                        matchQuantityConvertedStr = String.format("%.1f", convertedValue / 1e9) + " billion";
                    } else if (convertedValue >= 1e6) {
                        matchQuantityConvertedStr = String.format("%.1f", convertedValue / 1e6) + " million";
                    } else if (convertedValue >= 1e5) {
                        matchQuantityConvertedStr = String.format("%.0f", convertedValue / 1e3) + " thousand";
                    } else {
                        matchQuantityConvertedStr = String.format("%.2f", convertedValue);
                    }
                    matchQuantityConvertedStr += " (" + constraint.quantity.unit + ")";
                }

                inst.addSubInstance(new ResultInstance.SubInstance(f, matchScore.first, quantityStandardValue, matchQuantityConvertedStr, matchScore.second));
            }

            if (inst.subInstances.size() > 0) {
                Collections.sort(inst.subInstances, (o1, o2) -> Double.compare(o2.score, o1.score));
                result.second.add(inst);
            }

            i = j;
        }

        Collections.sort(result.second, (o1, o2) -> Double.compare(o2.score, o1.score));

        return result;
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext, String quantityConstraint) {
        return search(queryType, queryContext, quantityConstraint, null);
    }

    public static void main(String[] args) throws Exception {
    }
}
