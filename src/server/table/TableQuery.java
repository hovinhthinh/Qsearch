package server.table;

import misc.WikipediaView;
import model.context.ContextEmbeddingMatcher;
import model.context.IDF;
import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import nlp.Glove;
import nlp.NLP;
import org.eclipse.jetty.websocket.api.Session;
import storage.table.index.TableIndex;
import storage.table.index.TableIndexStorage;
import storage.text.migrate.TypeMatcher;
import uk.ac.susx.informatics.Morpha;
import util.Gson;
import util.Pair;
import util.headword.StringUtils;
import yago.TaxonomyGraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


public class TableQuery {
    public static final Logger LOGGER = Logger.getLogger(TableQuery.class.getName());
    public static double HEADER_MATCH_WEIGHT = 1;
    public static double CAPTION_MATCH_WEIGHT = 1; // old: 0.9
    public static double TITLE_MATCH_WEIGHT = 0.9; // old: 0.85
    public static double DOM_HEADING_MATCH_WEIGHT = 0.9; // old: 0.85
    public static double SAME_ROW_MATCH_WEIGHT = 0.9; // old: 0.85
    public static double RELATED_TEXT_MATCH_WEIGHT = 0.8;

    public static double TYPE_IDF_SCALE = 0.25;

    public static double TOPIC_DRIFT_PENALTY = 0.2;

    public static double QUANTITY_MATCH_WEIGHT = 0.03;
    public static double ENTITY_POPULARITY_WEIGHT = 0.045;

    public static final int N_TOP_ENTITY_CONSISTENCY_RESCORING = 200;

    private static ArrayList<QfactLight[]> QFACTS = TableQfactLoader.load();

    public static Pair<Double, ArrayList<ResultInstance.SubInstance.ContextMatchTrace>> match(
            ArrayList<String> queryTypeX, ArrayList<String> queryX,
            QfactLight f, TableIndex ti,
            Map params, Map<String, Object> matchCache) {
        if (params == null) {
            params = new HashMap<>();
        }

        double headerWeight = (double) params.getOrDefault("HEADER_MATCH_WEIGHT", HEADER_MATCH_WEIGHT);
        double captionWeight = (double) params.getOrDefault("CAPTION_MATCH_WEIGHT", CAPTION_MATCH_WEIGHT);
        double titleWeight = (double) params.getOrDefault("TITLE_MATCH_WEIGHT", TITLE_MATCH_WEIGHT);
        double domHeadingWeight = (double) params.getOrDefault("DOM_HEADING_MATCH_WEIGHT", DOM_HEADING_MATCH_WEIGHT);
        double sameRowWeight = (double) params.getOrDefault("SAME_ROW_MATCH_WEIGHT", SAME_ROW_MATCH_WEIGHT);
        double relatedTextWeight = (double) params.getOrDefault("RELATED_TEXT_MATCH_WEIGHT", RELATED_TEXT_MATCH_WEIGHT);

        double score = 0;
        ArrayList<ResultInstance.SubInstance.ContextMatchTrace> traces = new ArrayList<>();
        double totalIdf = 0;

        // matching queryType
        double typeIdfScale = (double) params.getOrDefault("TYPE_IDF_SCALE", TYPE_IDF_SCALE);

        String cacheKey = String.format("tX-%s", f.tableId);
        Object cacheValue;
        if ((cacheValue = matchCache.get(cacheKey)) != null) {
            Pair<Double, Double> p = (Pair<Double, Double>) cacheValue;
            score = p.first;
            totalIdf = p.second;
        } else {
            for (String qT : queryTypeX) {
                double matchScore = 0;
                // CAPTION
                if (matchScore < captionWeight) {
                    for (String fX : NLP.splitSentence(ti.caption.toLowerCase())) {
                        if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                            continue;
                        }
                        double sim = Glove.cosineDistance(qT, StringUtils.stem(fX, Morpha.any));
                        if (sim == -1) {
                            continue;
                        }
                        sim = (1 - sim) * captionWeight;

                        matchScore = Math.max(matchScore, sim);
                        if (matchScore >= captionWeight) {
                            break;
                        }
                    }
                }
                // TITLE
                if (matchScore < titleWeight) {
                    for (String fX : NLP.splitSentence(ti.pageTitle.toLowerCase())) {
                        if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                            continue;
                        }
                        double sim = Glove.cosineDistance(qT, StringUtils.stem(fX, Morpha.any));
                        if (sim == -1) {
                            continue;
                        }
                        sim = (1 - sim) * titleWeight;

                        matchScore = Math.max(matchScore, sim);
                        if (matchScore >= titleWeight) {
                            break;
                        }
                    }
                }

                double idf = IDF.getRobertsonIdf(qT) * typeIdfScale;
                score += matchScore * idf;
                totalIdf += idf;
            }
            matchCache.put(cacheKey, new Pair<>(score, totalIdf));
        }

        // matching contextX
        for (String qX : queryX) {
            ResultInstance.SubInstance.ContextMatchTrace traceCache, trace;

            // HEADER
            cacheKey = String.format("X_hd-%s-%d@%s", qX, f.qCol, f.tableId);
            if ((cacheValue = matchCache.get(cacheKey)) == null) {
                trace = new ResultInstance.SubInstance.ContextMatchTrace(null, 0, null);
                for (String fX : NLP.splitSentence(f.headerContext.toLowerCase())) {
                    if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                        continue;
                    }
                    double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                    if (sim == -1) {
                        continue;
                    }
                    sim = (1 - sim) * headerWeight;
                    if (trace.score < sim) {
                        trace.score = sim;
                        trace.token = fX;
                        trace.place = "HEADER";
                        if (trace.score >= headerWeight) {
                            break;
                        }
                    }
                }
                matchCache.put(cacheKey, trace);
            } else {
                trace = (ResultInstance.SubInstance.ContextMatchTrace) cacheValue;
            }

            if (trace.score < captionWeight
                    || trace.score < titleWeight
                    || trace.score < domHeadingWeight
                    || trace.score < relatedTextWeight) {
                cacheKey = String.format("X_ct_tt_dh_rt-%s-%s", qX, f.tableId);
                if (!matchCache.containsKey(cacheKey)) {
                    traceCache = new ResultInstance.SubInstance.ContextMatchTrace(null, 0, null);
                    // CAPTION
                    if (traceCache.score < captionWeight) {
                        for (String fX : NLP.splitSentence(ti.caption.toLowerCase())) {
                            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                                continue;
                            }
                            double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                            if (sim == -1) {
                                continue;
                            }
                            sim = (1 - sim) * captionWeight;

                            if (traceCache.score < sim) {
                                traceCache.score = sim;
                                traceCache.token = fX;
                                traceCache.place = "CAPTION";
                                if (traceCache.score >= captionWeight) {
                                    break;
                                }
                            }
                        }
                    }
                    // TITLE
                    if (traceCache.score < titleWeight) {
                        for (String fX : NLP.splitSentence(ti.pageTitle.toLowerCase())) {
                            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                                continue;
                            }
                            double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                            if (sim == -1) {
                                continue;
                            }
                            sim = (1 - sim) * titleWeight;

                            if (traceCache.score < sim) {
                                traceCache.score = sim;
                                traceCache.token = fX;
                                traceCache.place = "TITLE";
                                if (traceCache.score >= titleWeight) {
                                    break;
                                }
                            }
                        }
                    }
                    // DOM HEADINGs
                    if (traceCache.score < domHeadingWeight) {
                        for (String fX : NLP.splitSentence(ti.sectionTitles.toLowerCase())) {
                            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                                continue;
                            }
                            double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                            if (sim == -1) {
                                continue;
                            }
                            sim = (1 - sim) * domHeadingWeight;

                            if (traceCache.score < sim) {
                                traceCache.score = sim;
                                traceCache.token = fX;
                                traceCache.place = "DOM_HEADING";
                                if (traceCache.score >= domHeadingWeight) {
                                    break;
                                }
                            }
                        }
                    }
                    // RELATED_TEXT
                    if (traceCache.score < relatedTextWeight) {
                        for (String fX : NLP.splitSentence(ti.pageContent.toLowerCase())) {
                            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                                continue;
                            }
                            double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                            if (sim == -1) {
                                continue;
                            }
                            sim = (1 - sim) * relatedTextWeight;

                            if (traceCache.score < sim) {
                                traceCache.score = sim;
                                traceCache.token = fX;
                                traceCache.place = "RELATED_TEXT";
                                if (traceCache.score >= relatedTextWeight) {
                                    break;
                                }
                            }
                        }
                    }
                    matchCache.put(cacheKey, traceCache);
                }
                traceCache = (ResultInstance.SubInstance.ContextMatchTrace) matchCache.get(cacheKey);
                if (trace.score < traceCache.score) {
                    trace = traceCache;
                }
            }

            // SAME ROW
            if (trace.score < sameRowWeight) {
                traceCache = new ResultInstance.SubInstance.ContextMatchTrace(null, 0, null);
                loop:
                for (int c = 0; c < ti.table.nColumn; ++c) {
                    if (c == f.eCol || c == f.qCol) {
                        continue;
                    }
                    for (String fX : NLP.splitSentence(ti.table.data[f.row][c].text.toLowerCase())) {
                        if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                            continue;
                        }
                        double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                        if (sim == -1) {
                            continue;
                        }
                        sim = (1 - sim) * sameRowWeight;

                        if (traceCache.score < sim) {
                            traceCache.score = sim;
                            traceCache.token = fX;
                            traceCache.place = "SAME_ROW";
                            if (traceCache.score >= sameRowWeight) {
                                break loop;
                            }
                        }
                    }
                }
                if (trace.score < traceCache.score) {
                    trace = traceCache;
                }
            }

            if (trace.score > 0) {
                boolean toBeAdded = true;
                for (int i = 0; i < traces.size(); ++i) {
                    ResultInstance.SubInstance.ContextMatchTrace t = traces.get(i);
                    if (t.token.equals(trace.token) && t.place.equals(trace.place)) {
                        if (t.score < trace.score) {
                            traces.set(i, trace);
                        }
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

        // Penalty by topic drift between header & query
        double topicDriftWeight = (double) params.getOrDefault("TOPIC_DRIFT_PENALTY", TOPIC_DRIFT_PENALTY);
        if (topicDriftWeight != 0.0) {
            cacheKey = String.format("td-%d@%s", f.qCol, f.tableId);
            if ((cacheValue = matchCache.get(cacheKey)) == null) {
                ArrayList<String> header = new ArrayList<>();
                for (String fX : NLP.splitSentence(f.headerContext.toLowerCase())) {
                    if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                        continue;
                    }
                    header.add(StringUtils.stem(fX, Morpha.any));
                }
                matchCache.put(cacheKey, cacheValue =
                        Math.pow(ContextEmbeddingMatcher.directedEmbeddingIdfSimilarity(header, queryX), topicDriftWeight));
            }
            score *= (double) cacheValue;
        }
        return new Pair<>(score, traces);
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext, String quantityConstraint,
                                                                             boolean performConsistencyRescoring,
                                                                             Map additionalParameters) {

        return search(queryType, queryContext, QuantityConstraint.parseFromString(quantityConstraint), performConsistencyRescoring, additionalParameters);
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext, QuantityConstraint qtConstraint,
                                                                             boolean performConsistencyRescoring,
                                                                             Map additionalParameters) {
        Pair<QuantityConstraint, ArrayList<ResultInstance>> result = new Pair<>();

        result.first = qtConstraint;
        if (qtConstraint == null) {
            return result;
        }
        double qtConstraintStandardValue = qtConstraint.quantity.value * qtConstraint.quantity.getScale();
        Double qtConstraintStandardValue2 = qtConstraint.quantity.value2 == null ? null :
                qtConstraint.quantity.value2 * qtConstraint.quantity.getScale();

        TypeMatcher typeMatcher = new TypeMatcher(queryType);

        // Build query type-context terms, head word and query type set
        ArrayList<String> queryTypeTerms = NLP.splitSentence(NLP.fastStemming(typeMatcher.queryYagoTypeId != null
                ? TaxonomyGraph.getDefaultGraphInstance().id2TextualizedType.get(typeMatcher.queryYagoTypeId) // using id
                : queryType.toLowerCase(), Morpha.any)); // raw type
        for (int i = queryTypeTerms.size() - 1; i >= 0; --i) {
            String t = queryTypeTerms.get(i);
            if (NLP.BLOCKED_STOPWORDS.contains(t) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(t)) {
                queryTypeTerms.remove(i);
            }
        }

        // Process query context terms
        String domain = qtConstraint.quantity.getSearchDomain();
        if (domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            queryContext += " " + qtConstraint.quantity.unit;
        }
        queryContext = NLP.fastStemming(queryContext.toLowerCase(), Morpha.any);
        // expand with domain name if empty
        if (queryContext.isEmpty() && !domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            queryContext = NLP.stripSentence(domain.toLowerCase());
        }
        ArrayList<String> queryContextTerms = NLP.splitSentence(queryContext);

        // Corpus constraint
        ArrayList<String> corpusConstraint = new ArrayList<>();
        corpusConstraint = additionalParameters == null ? null :
                (additionalParameters.containsKey("corpus") ? Gson.fromJson((String) additionalParameters.get("corpus"), corpusConstraint.getClass()) : null);

        // linking threshold
        double linkingThreshold = additionalParameters != null && additionalParameters.containsKey("linking-threshold")
                ? (double) additionalParameters.get("linking-threshold") : -1; // default is no-threshold

        // retrieve additional parameters
        Session session = additionalParameters == null ? null : (Session) additionalParameters.get("session");
        double qtMatchWeight = additionalParameters == null ? QUANTITY_MATCH_WEIGHT :
                (double) additionalParameters.getOrDefault("QUANTITY_MATCH_WEIGHT", QUANTITY_MATCH_WEIGHT);
        double entityPopularityWeight = additionalParameters == null ? ENTITY_POPULARITY_WEIGHT :
                (double) additionalParameters.getOrDefault("ENTITY_POPULARITY_WEIGHT", ENTITY_POPULARITY_WEIGHT);

        int lastPercent = 0;

        HashMap<String, Object> matchCache = new HashMap<>(1000000);
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

            QfactLight[] entityQFacts = QFACTS.get(i);

            ResultInstance inst = new ResultInstance();
            inst.entity = "<" + entityQFacts[0].entity.substring(5) + ">";

            // process type
            if (!typeMatcher.match(inst.entity)) {
                continue;
            }

            inst.popularity = WikipediaView.getView(inst.entity);

            for (QfactLight f : entityQFacts) {
                if (linkingThreshold != -1 && f.linkingScore < linkingThreshold) {
                    continue;
                }
                // quantity
                Quantity qt = Quantity.fromQuantityString(f.quantity);
                if (!qtConstraint.match(qt)) {
                    continue;
                }

                TableIndex ti = TableIndexStorage.get(f.tableId);
                // check corpus target
                if (corpusConstraint != null) {
                    boolean goodSource = false;
                    for (String c : corpusConstraint) {
                        if (ti.table.source.startsWith(c + ":")) {
                            goodSource = true;
                            break;
                        }
                    }
                    if (!goodSource) {
                        continue;
                    }
                }

                Pair<Double, ArrayList<ResultInstance.SubInstance.ContextMatchTrace>> matchScore =
                        match(queryTypeTerms, queryContextTerms, f, ti, additionalParameters, matchCache);

                double qtStandardValue = qt.value * qt.getScale();

                // compute quantity distance to query
                double qtRelativeDist = Math.min(1,
                        Math.abs(qtStandardValue - qtConstraintStandardValue)
                                / Math.max(Math.abs(qtStandardValue), Math.abs(qtConstraintStandardValue))
                );
                if (qtConstraintStandardValue2 != null) {
                    qtRelativeDist = Math.min(qtRelativeDist,
                            Math.abs(qtStandardValue - qtConstraintStandardValue2)
                                    / Math.max(Math.abs(qtStandardValue), Math.abs(qtConstraintStandardValue2))
                    );
                }

                // entity popularity would be added later
                matchScore.first = qtMatchWeight * (1 - qtRelativeDist)
                        + (1 - qtMatchWeight - entityPopularityWeight) * matchScore.first;

//                if (matchScore.first < 0.7) {
//                    continue;
//                }

                inst.addSubInstance(new ResultInstance.SubInstance(f, matchScore.first,
                        qtStandardValue, qt.getQuantityConvertedStr(qtConstraint.quantity), matchScore.second));
            }

            if (inst.subInstances.size() > 0) {
                Collections.sort(inst.subInstances, (o1, o2) -> Double.compare(o2.score, o1.score));
                result.second.add(inst);
            }
        }

        // add entity popularity
        int maxPopularity = 1;
        for (ResultInstance ri : result.second) {
            maxPopularity = Math.max(maxPopularity, ri.popularity);
        }
        for (ResultInstance ri : result.second) {
            double entityPopularityContribution = Math.max(1.0 * ri.popularity, 0.0) / maxPopularity;
            ri.score += entityPopularityWeight * entityPopularityContribution;
            for (ResultInstance.SubInstance si : ri.subInstances) {
                si.score += entityPopularityWeight * entityPopularityContribution;
            }
        }

        Collections.sort(result.second);

        // Consistency rescoring
        if (performConsistencyRescoring) {
            int nEntityRescoring = Math.min(N_TOP_ENTITY_CONSISTENCY_RESCORING, result.second.size());
            ArrayList<ResultInstance.SubInstance> qfacts = new ArrayList<>();
            for (int i = 0; i < nEntityRescoring; ++i) {
                qfacts.addAll(result.second.get(i).subInstances);
            }
            QfactLightConsistencyRescoringEngine.consistencyBasedRescore(qfacts, additionalParameters, session);
            for (int i = 0; i < nEntityRescoring; ++i) {
                ResultInstance ri = result.second.get(i);
                Collections.sort(ri.subInstances, (o1, o2) -> Double.compare(o2.rescore, o1.rescore));
                ri.score = ri.subInstances.get(0).rescore;
            }
            Collections.sort(result.second.subList(0, nEntityRescoring));
        }

        return result;
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext, String quantityConstraint) {
        return search(queryType, queryContext, quantityConstraint, false, null);
    }
}
