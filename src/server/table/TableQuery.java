package server.table;

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
import uk.ac.susx.informatics.Morpha;
import util.Gson;
import util.Pair;
import util.headword.StringUtils;
import yago.TaxonomyGraph;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;


public class TableQuery {
    public static final Logger LOGGER = Logger.getLogger(TableQuery.class.getName());
    public static double HEADER_MATCH_WEIGHT = 1;
    public static double CAPTION_MATCH_WEIGHT = 1; // old: 0.9
    public static double TITLE_MATCH_WEIGHT = 0.9; // old: 0.85
    public static double SAME_ROW_MATCH_WEIGHT = 0.9; // old: 0.85
    public static double RELATED_TEXT_MATCH_WEIGHT = 0.8;

    public static double QUANTITY_MATCH_WEIGHT = 0.075; // old: 0.025

    public static final int N_TOP_ENTITY_CONSISTENCY_RESCORING = 200;

    private static ArrayList<QfactLight> QFACTS = TableQfactLoader.load();
    private static TaxonomyGraph TAXONOMY = TaxonomyGraph.getDefaultGraphInstance();

    public static Pair<Double, ArrayList<ResultInstance.SubInstance.ContextMatchTrace>> match(ArrayList<String> queryX, QfactLight f, TableIndex ti, Map params) {
        if (params == null) {
            params = new HashMap<>();
        }

        double headerWeight = (double) params.getOrDefault("HEADER_MATCH_WEIGHT", HEADER_MATCH_WEIGHT);
        double captionWeight = (double) params.getOrDefault("CAPTION_MATCH_WEIGHT", CAPTION_MATCH_WEIGHT);
        double titleWeight = (double) params.getOrDefault("TITLE_MATCH_WEIGHT", TITLE_MATCH_WEIGHT);
        double sameRowWeight = (double) params.getOrDefault("SAME_ROW_MATCH_WEIGHT", SAME_ROW_MATCH_WEIGHT);
        double relatedTextWeight = (double) params.getOrDefault("RELATED_TEXT_MATCH_WEIGHT", RELATED_TEXT_MATCH_WEIGHT);

        double score = 0;
        ArrayList<ResultInstance.SubInstance.ContextMatchTrace> traces = new ArrayList<>();
        double totalIdf = 0;

        ArrayList<String> header = new ArrayList<>();
        for (String qX : queryX) {
            ResultInstance.SubInstance.ContextMatchTrace trace = new ResultInstance.SubInstance.ContextMatchTrace(null, 0, null);
            // HEADER
            for (String fX : NLP.splitSentence(f.headerContext.toLowerCase())) {
                if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                    continue;
                }
                fX = StringUtils.stem(fX, Morpha.any);
                header.add(fX);
                double sim = Glove.cosineDistance(qX, fX);
                if (sim == -1) {
                    continue;
                }
                sim = (1 - sim) * headerWeight;

                if (trace.score < sim) {
                    trace.score = sim;
                    trace.token = fX;
                    trace.place = "HEADER";
                }
            }
            // CAPTION
            if (trace.score < captionWeight) {
                for (String fX : NLP.splitSentence(ti.caption.toLowerCase())) {
                    if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                        continue;
                    }
                    double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                    if (sim == -1) {
                        continue;
                    }
                    sim = (1 - sim) * captionWeight;

                    if (trace.score < sim) {
                        trace.score = sim;
                        trace.token = fX;
                        trace.place = "CAPTION";
                        if (trace.score >= captionWeight) {
                            break;
                        }
                    }
                }
            }
            // TITLE
            if (trace.score < titleWeight) {
                loop:
                for (String title : Arrays.asList(ti.pageTitle, ti.sectionTitles)) {
                    for (String fX : NLP.splitSentence(title.toLowerCase())) {
                        if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                            continue;
                        }
                        double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                        if (sim == -1) {
                            continue;
                        }
                        sim = (1 - sim) * titleWeight;

                        if (trace.score < sim) {
                            trace.score = sim;
                            trace.token = fX;
                            trace.place = "TITLE";
                            if (trace.score >= titleWeight) {
                                break loop;
                            }
                        }
                    }
                }
            }
            // SAME ROW
            if (trace.score < sameRowWeight) {
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

                        if (trace.score < sim) {
                            trace.score = sim;
                            trace.token = fX;
                            trace.place = "SAME_ROW";
                            if (trace.score >= sameRowWeight) {
                                break loop;
                            }
                        }
                    }
                }
            }

            // RELATED_TEXT
            if (trace.score < relatedTextWeight) {
                for (String fX : NLP.splitSentence(ti.pageContent.toLowerCase())) {
                    if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                        continue;
                    }
                    double sim = Glove.cosineDistance(qX, StringUtils.stem(fX, Morpha.any));
                    if (sim == -1) {
                        continue;
                    }
                    sim = (1 - sim) * relatedTextWeight;

                    if (trace.score < sim) {
                        trace.score = sim;
                        trace.token = fX;
                        trace.place = "RELATED_TEXT";
                        if (trace.score >= relatedTextWeight) {
                            break;
                        }
                    }
                }
            }

            if (trace.score > 0) {
                boolean toBeAdded = true;
                for (ResultInstance.SubInstance.ContextMatchTrace t : traces) {
                    if (t.token.equals(trace.token) && t.place.equals(trace.place)) {
                        toBeAdded = false;
                        t.score = Math.max(t.score, trace.score);
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

        // Penalty by difference between header & query
        score *= ContextEmbeddingMatcher.directedEmbeddingIdfSimilarity(header, queryX);
        return new Pair<>(score, traces);
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String queryType, String queryContext, String quantityConstraint,
                                                                             boolean performConsistencyRescoring,
                                                                             Map additionalParameters) {
        Pair<QuantityConstraint, ArrayList<ResultInstance>> result = new Pair<>();

        QuantityConstraint qtConstraint = QuantityConstraint.parseFromString(quantityConstraint);
        result.first = qtConstraint;
        if (qtConstraint == null) {
            return result;
        }
        double qtConstraintStandardValue = qtConstraint.quantity.value * QuantityDomain.getScale(qtConstraint.quantity);
        Double qtConstraintStandardValue2 = qtConstraint.quantity.value2 == null ? null :
                qtConstraint.quantity.value2 * QuantityDomain.getScale(qtConstraint.quantity);

        // Build query head word and query type set
        queryType = NLP.stripSentence(NLP.fastStemming(queryType.toLowerCase(), Morpha.noun));
        String queryHeadWord = NLP.getHeadWord(queryType, true);

        // Process query context terms
        String domain = QuantityDomain.getDomain(qtConstraint.quantity);
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

            String entity = QFACTS.get(i).entity;

            boolean hasQfactWithGoodLinkingThreshold = false;
            int j = i - 1;
            QfactLight tempQfact;
            while (j < QFACTS.size() - 1 && (tempQfact = QFACTS.get(j + 1)).entity.equals(entity)) {
                ++j;
                if (linkingThreshold == -1 || tempQfact.linkingScore >= linkingThreshold) {
                    hasQfactWithGoodLinkingThreshold = true;
                }
            }

            if (!hasQfactWithGoodLinkingThreshold) {
                i = j;
                continue;
            }

            // process type
            boolean entityIsOfCorrectType = false;
            for (String type : TAXONOMY.getTextualizedTypes("<" + entity.substring(5) + ">", true)) {
                if (type.contains(queryType) && queryHeadWord.equals(NLP.getHeadWord(type, true))) {
                    entityIsOfCorrectType = true;
                    break;
                }
            }
            if (!entityIsOfCorrectType) {
                i = j;
                continue;
            }

            ResultInstance inst = new ResultInstance();
            inst.entity = "<" + entity.substring(5) + ">";

            for (int k = i; k <= j; ++k) {
                QfactLight f = QFACTS.get(k);
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

                Pair<Double, ArrayList<ResultInstance.SubInstance.ContextMatchTrace>> matchScore = match(queryContextTerms, f, ti, additionalParameters);

                double qtStandardValue = qt.value * QuantityDomain.getScale(qt);

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

                matchScore.first = qtMatchWeight * (1 - qtRelativeDist) + (1 - qtMatchWeight) * matchScore.first;

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

            i = j;
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
