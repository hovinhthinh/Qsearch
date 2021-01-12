package storage.text.migrate;

import misc.WikipediaView;
import model.context.ContextEmbeddingMatcher;
import model.context.ContextMatcher;
import model.context.KullBackLeiblerMatcher;
import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import nlp.NLP;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONException;
import server.text.ResultInstance;
import uk.ac.susx.informatics.Morpha;
import util.Constants;
import util.Gson;
import util.Pair;
import util.headword.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

public class ChronicleMapQuery {
    public static final Logger LOGGER = Logger.getLogger(ChronicleMapQuery.class.getName());

    public static ContextMatcher DEFAULT_MATCHER = new ContextEmbeddingMatcher(3);

    public static double QUANTITY_MATCH_WEIGHT = 0.0;
    public static double ENTITY_POPULARITY_WEIGHT = 0.0;

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext,
                                                                             String quantityConstraint,
                                                                             ContextMatcher matcher, Map additionalParameters) {
        return search(queryType, queryContext, QuantityConstraint.parseFromString(quantityConstraint), matcher, additionalParameters);
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext,
                                                                             QuantityConstraint quantityConstraint,
                                                                             ContextMatcher matcher, Map additionalParameters) {
        Pair<QuantityConstraint, ArrayList<ResultInstance>> result = new Pair<>();

        result.first = quantityConstraint;
        if (quantityConstraint == null) {
            return result;
        }
        double qtConstraintStandardValue = quantityConstraint.quantity.value * QuantityDomain.getScale(quantityConstraint.quantity);
        Double qtConstraintStandardValue2 = quantityConstraint.quantity.value2 == null ? null :
                quantityConstraint.quantity.value2 * QuantityDomain.getScale(quantityConstraint.quantity);

        TypeMatcher typeMatcher = new TypeMatcher(queryType);

        // Process query context terms
        queryContext = queryContext.toLowerCase();
        String domain = QuantityDomain.getDomain(quantityConstraint.quantity);
        if (domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            queryContext += " " + quantityConstraint.quantity.unit;
        }
        queryContext = NLP.fastStemming(queryContext.toLowerCase(), Morpha.any);
        // expand with domain name if empty
        if (queryContext.isEmpty() && !domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            queryContext = NLP.stripSentence(domain.toLowerCase());
        }
        ArrayList<String> queryContextTerms = NLP.splitSentence(queryContext);

        ArrayList<ResultInstance> scoredInstances = new ArrayList<>();
        int lastPercent = 0;

        // retrieve additional parameters
        Session session = additionalParameters == null ? null : (Session) additionalParameters.get("session");
        double qtMatchWeight = additionalParameters == null ? QUANTITY_MATCH_WEIGHT :
                (double) additionalParameters.getOrDefault("QUANTITY_MATCH_WEIGHT", QUANTITY_MATCH_WEIGHT);
        double entityPopularityWeight = additionalParameters == null ? ENTITY_POPULARITY_WEIGHT :
                (double) additionalParameters.getOrDefault("ENTITY_POPULARITY_WEIGHT", ENTITY_POPULARITY_WEIGHT);

        ArrayList<String> corpusConstraint = new ArrayList<>();
        corpusConstraint = additionalParameters == null ? null :
                (additionalParameters.containsKey("corpus") ? Gson.fromJson((String) additionalParameters.get("corpus"), corpusConstraint.getClass()) : null);

        String explicitMatchingModel = additionalParameters == null ? null : (String) additionalParameters.get("model");
        ContextMatcher explicitMatcher = null;
        if (explicitMatchingModel != null) {
            if (explicitMatchingModel.equals("EMBEDDING")) {
                explicitMatcher = new ContextEmbeddingMatcher((double) additionalParameters.get("alpha"));
            } else {
                explicitMatcher = new KullBackLeiblerMatcher((double) additionalParameters.get("lambda"));
            }
            LOGGER.info("Using explicitly given matcher: " + explicitMatchingModel);
        }

        try {
            // for each entity.
            for (int it = 0; it < ChronicleMapQfactStorage.SEARCHABLE_ENTITIES.size(); ++it) {
                // log progress
                if (session != null) {
                    int currentPercent = (int) ((double) (it + 1) * 100 / ChronicleMapQfactStorage.SEARCHABLE_ENTITIES.size());
                    if (currentPercent > lastPercent) {
                        lastPercent = currentPercent;
                        try {
                            session.getRemote().sendString("{\"progress\":" + currentPercent + "}");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                String entity = ChronicleMapQfactStorage.SEARCHABLE_ENTITIES.get(it);

                // process type
                if (!typeMatcher.match(entity)) {
                    continue;
                }

//                ArrayList<String> matchContext = new ArrayList<>();

                // computer score
                JSONArray facts = ChronicleMapQfactStorage.get(entity);
                // save space.
                ResultInstance r = new ResultInstance();
                r.score = Constants.MAX_DOUBLE;
                r.entity = entity;
                r.popularity = WikipediaView.getView(r.entity);
                for (int i = 0; i < facts.length(); ++i) {
                    // check corpus target
                    if (corpusConstraint != null) {
                        boolean goodSource = false;
                        for (String c : corpusConstraint) {
                            if (facts.getJSONObject(i).getString("source").startsWith(c + ":")) {
                                goodSource = true;
                                break;
                            }
                        }
                        if (!goodSource) {
                            continue;
                        }
                    }

                    String Q = facts.getJSONObject(i).getString("quantity");
                    Quantity qt = Quantity.fromQuantityString(Q);
                    if (!quantityConstraint.match(qt)) {
                        continue;
                    }

                    ArrayList<String> X = new ArrayList<>();
                    JSONArray context = facts.getJSONObject(i).getJSONArray("context");
                    for (int k = 0; k < context.length(); ++k) {
                        String ct = context.getString(k);
                        if (ct.startsWith("<T>:")) {
                            // handle time like normal terms.
                            X.addAll(NLP.splitSentence(ct.substring(4)));
                        } else if (ct.startsWith("<E>:")) {
                            X.addAll(NLP.splitSentence(ct.substring(4)));
                        } else {
                            X.add(ct);
                        }
                    }

                    ArrayList<String> contextVerbose = new ArrayList<>(X);

                    if (QuantityDomain.quantityMatchesDomain(qt, QuantityDomain.Domain.DIMENSIONLESS)) {
                        X.addAll(NLP.splitSentence(qt.unit));
                    }
                    if (X.isEmpty()) {
                        continue;
                    }
                    for (int j = 0; j < X.size(); ++j) {
                        X.set(j, StringUtils.stem(X.get(j).toLowerCase(), Morpha.any));
                    }
                    // use explicit matcher if given.
                    double dist = explicitMatcher != null ? explicitMatcher.match(queryContextTerms, X) : matcher.match(queryContextTerms, X);

                    if (dist >= Constants.MAX_DOUBLE) {
                        continue;
                    }

                    ResultInstance.SubInstance si = new ResultInstance.SubInstance();
                    si.quantity = qt.toString(1);
                    si.quantityStandardValue = qt.value * QuantityDomain.getScale(qt);
                    si.quantityStr = facts.getJSONObject(i).getString("quantityStr");
                    si.quantityConvertedStr = qt.getQuantityConvertedStr(quantityConstraint.quantity);

                    si.entityStr = facts.getJSONObject(i).getString("entityStr");

                    si.contextStr = contextVerbose;
//                        matchContext = new ArrayList<>(X);

                    si.sentence = facts.getJSONObject(i).getString("sentence");
                    si.source = facts.getJSONObject(i).getString("source");

                    // compute quantity distance to query
                    double qtRelativeDist = Math.min(1,
                            Math.abs(si.quantityStandardValue - qtConstraintStandardValue)
                                    / Math.max(Math.abs(si.quantityStandardValue), Math.abs(qtConstraintStandardValue))
                    );
                    if (qtConstraintStandardValue2 != null) {
                        qtRelativeDist = Math.min(qtRelativeDist,
                                Math.abs(si.quantityStandardValue - qtConstraintStandardValue2)
                                        / Math.max(Math.abs(si.quantityStandardValue), Math.abs(qtConstraintStandardValue2))
                        );
                    }

                    // entity popularity would be added later
                    si.score = qtMatchWeight * qtRelativeDist
                            + (1 - qtMatchWeight - entityPopularityWeight) * dist;

                    r.addSubInstance(si);
                }
                if (r.subInstances.size() > 0) {
                    Collections.sort(r.subInstances, (o1, o2) -> Double.compare(o1.score, o2.score));
                    scoredInstances.add(r);
                }
            }

            // add entity popularity
            int maxPopularity = 1;
            for (ResultInstance ri : scoredInstances) {
                maxPopularity = Math.max(maxPopularity, ri.popularity);
            }
            for (ResultInstance ri : scoredInstances) {
                double entityPopularityContribution = Math.max(1.0 * ri.popularity, 0.0) / maxPopularity;
                ri.score += entityPopularityWeight * (1 - entityPopularityContribution);
                for (ResultInstance.SubInstance si : ri.subInstances) {
                    si.score += entityPopularityWeight * (1 - entityPopularityContribution);
                }
            }

            Collections.sort(scoredInstances);

            result.second = scoredInstances;
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            return result;
        }
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext,
                                                                             String quantityConstraint,
                                                                             ContextMatcher matcher) {
        return search(queryType, queryContext, quantityConstraint, matcher, null);
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext,
                                                                             String quantityConstraint) {
        return search(queryType, queryContext, quantityConstraint, DEFAULT_MATCHER);
    }

    public static void main(String[] args) {
        ArrayList<ResultInstance> result =
                search("car",
                        "consumption",
                        "more than 0 mpg").second;

        int nPrinted = 0;
        for (ResultInstance o : result) {
            ResultInstance.SubInstance si = o.subInstances.get(0);
            try {
                if (nPrinted < 20) {
                    System.out.println(String.format("%30s\t%10.3f\t%50s\t%20s\t%s\t%s", o.entity,
                            si.score, Gson.toJson(si.contextStr), si.quantity, si.sentence, si.source));
                    ++nPrinted;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
