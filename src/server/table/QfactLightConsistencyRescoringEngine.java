package server.table;

import model.context.IDF;
import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import nlp.NLP;
import org.eclipse.jetty.websocket.api.Session;
import storage.table.index.TableIndex;
import storage.table.index.TableIndexStorage;
import uk.ac.susx.informatics.Morpha;
import util.Pair;
import util.headword.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

class KNNEstimator {
    public List<DataPoint> training;
    public int k;
    private double quantityMeanNormalizeValue;

    public static class DataPoint {
        public ResultInstance.SubInstance si;
        public HashMap<String, Double> termTfidfMap;
        public double consistencyScr;
        public int nProbeTimes;

        public DataPoint(ResultInstance.SubInstance si, HashMap<String, Double> termTfidfMap) {
            this.si = si;
            this.termTfidfMap = termTfidfMap;
            this.consistencyScr = 0;
            this.nProbeTimes = 0;
        }

        // using Vector Space Model here, computing Cosine similarity
        public double dist(DataPoint o, double quantityMeanValue) {
            double dotProd = 0;
            double length = 0, oLength = 0;

            // tfidf
            for (Map.Entry<String, Double> e : termTfidfMap.entrySet()) {
                double oF = o.termTfidfMap.getOrDefault(e, 0.0);
                dotProd += e.getValue() * oF;
                length += e.getValue() * e.getValue();
                oLength += oF * oF;
            }
            for (Map.Entry<String, Double> e : o.termTfidfMap.entrySet()) {
                if (termTfidfMap.containsKey(e.getKey())) {
                    continue;
                }
                oLength += e.getValue() * e.getValue();
            }

            // quantity values
            Quantity q = Quantity.fromQuantityString(si.qfact.quantity);
            Quantity oQ = Quantity.fromQuantityString(o.si.qfact.quantity);

            double qValue = q.value * QuantityDomain.getScale(q) / quantityMeanValue * QfactLightConsistencyRescoringEngine.QUANTITY_FEATURE_BOOST;
            double oQValue = oQ.value * QuantityDomain.getScale(oQ) / quantityMeanValue * QfactLightConsistencyRescoringEngine.QUANTITY_FEATURE_BOOST;
            dotProd += qValue * oQValue;
            length += qValue * qValue;
            oLength += oQValue * oQValue;

            return 0.5 - dotProd / Math.sqrt(length) / Math.sqrt(oLength) / 2;
        }
    }

    public KNNEstimator(List<DataPoint> training, int k) {
        this.training = training;
        this.k = k;
        this.quantityMeanNormalizeValue = 0;
        for (DataPoint p : training) {
            Quantity q = Quantity.fromQuantityString(p.si.qfact.quantity);
            this.quantityMeanNormalizeValue += Math.abs(q.value * QuantityDomain.getScale(q)) / training.size();
        }
    }

    public double estimate(DataPoint p) {
        ArrayList<Pair<Double, DataPoint>> queue = new ArrayList<>();

        // kNN
        loop:
        for (DataPoint t : training) {
            if (p.si.qfact.tableId.equals(t.si.qfact.tableId)) {
                continue;
            }
            Pair<Double, DataPoint> dist2Point = new Pair<>(p.dist(t, quantityMeanNormalizeValue), t);

            // update if there is a worse fact of the same table in queue.
            for (int i = 0; i < queue.size(); ++i) {
                Pair<Double, DataPoint> o = queue.get(i);
                if (o.second.si.qfact.tableId.equals(t.si.qfact.tableId)) {
                    if (dist2Point.first < o.first) {
                        queue.set(i, dist2Point);
                        continue loop;
                    }
                    break;
                }
            }

            queue.add(dist2Point);
            // remove the furthest point
            if (queue.size() > k) {
                int posToRemove = 0;
                for (int i = 1; i < queue.size(); ++i) {
                    if (queue.get(i).first > queue.get(posToRemove).first) {
                        posToRemove = i;
                    }
                }

                queue.set(posToRemove, queue.get(queue.size() - 1));
                queue.remove(queue.size() - 1);
            }
        }

        if (queue.size() == 0) {
            return p.si.score;
        }

        double res = 0;
        for (Pair<Double, DataPoint> dist2Point : queue) {
            res += dist2Point.second.si.score / queue.size();
        }
        return res;
    }
}

public class QfactLightConsistencyRescoringEngine {
    public static final Logger LOGGER = Logger.getLogger(QfactLightConsistencyRescoringEngine.class.getName());

    // TODO: adjust these params.
    public static double HEADER_TF_WEIGHT = TableQuery.HEADER_MATCH_WEIGHT;
    public static double CAPTION_TF_WEIGHT = TableQuery.CAPTION_MATCH_WEIGHT;
    public static double TITLE_TF_WEIGHT = TableQuery.TITLE_MATCH_WEIGHT;
    public static double SAME_ROW_TF_WEIGHT = TableQuery.SAME_ROW_MATCH_WEIGHT;
    public static double RELATED_TEXT_TF_WEIGHT = 0;
    public static double QUANTITY_FEATURE_BOOST = 10;

    // params for consistency learning
    public static int CONSISTENCY_LEARNING_N_FOLD = 100;
    public static double CONSISTENCY_LEARNING_PROBE_RATE = 0.3;
    public static int KNN_ESTIMATOR_K = 3;
    public static double INTERPOLATION_WEIGHT = 0.1;

    private static HashMap<String, Double> qfactLight2TermTfidfMap(QfactLight f, TableIndex ti) {
        HashMap<String, Double> termTfidfMap = new HashMap<>();

        // HEADER
        for (String fX : NLP.splitSentence(f.headerContext.toLowerCase())) {
            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                continue;
            }
            fX = StringUtils.stem(fX, Morpha.any);
            termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + HEADER_TF_WEIGHT);
        }
        // CAPTION
        for (String fX : NLP.splitSentence(ti.caption.toLowerCase())) {
            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                continue;
            }
            fX = StringUtils.stem(fX, Morpha.any);
            termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + CAPTION_TF_WEIGHT);
        }
        // TITLES
        for (String title : Arrays.asList(ti.pageTitle, ti.sectionTitles)) {
            for (String fX : NLP.splitSentence(title)) {
                if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                    continue;
                }
                fX = StringUtils.stem(fX, Morpha.any);
                termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + TITLE_TF_WEIGHT);
            }
        }
        // SAME ROW
        for (int c = 0; c < ti.table.nColumn; ++c) {
            if (c == f.eCol || c == f.qCol) {
                continue;
            }
            for (String fX : NLP.splitSentence(ti.table.data[f.row][c].text.toLowerCase())) {
                if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                    continue;
                }
                fX = StringUtils.stem(fX, Morpha.any);
                termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + SAME_ROW_TF_WEIGHT);
            }
        }
        // PAGE CONTENT
        if (RELATED_TEXT_TF_WEIGHT > 0) {
            for (String fX : NLP.splitSentence(ti.pageContent.toLowerCase())) {
                if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                    continue;
                }
                fX = StringUtils.stem(fX, Morpha.any);
                termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + RELATED_TEXT_TF_WEIGHT);
            }
        }

        double sum = 0;
        for (Double tf : termTfidfMap.values()) {
            sum += tf;
        }
        final double fSum = sum;

        // normalize tf, and multiply with idf
        termTfidfMap.replaceAll((k, v) -> (fSum == 0 ? 0 : v / fSum) * IDF.getRobertsonIdf(k));
        return termTfidfMap;
    }

    public static void consistencyBasedRescore(ArrayList<ResultInstance.SubInstance> priorScoredQfacts, Session session) {
        ArrayList<KNNEstimator.DataPoint> candidates = new ArrayList<>();

        for (int i = 0; i < priorScoredQfacts.size(); ++i) {
            ResultInstance.SubInstance si = priorScoredQfacts.get(i);
            si.rescore = si.score;
            candidates.add(new KNNEstimator.DataPoint(si, qfactLight2TermTfidfMap(si.qfact, TableIndexStorage.get(si.qfact.tableId))));
        }

        // Now perform consistency-based re-scoring.
        int nProbe = (int) (candidates.size() * CONSISTENCY_LEARNING_PROBE_RATE + 1e-6);
        if (nProbe == 0) {
            return;
        }

        int lastPercent = 0;
        for (int i = 0; i < CONSISTENCY_LEARNING_N_FOLD; ++i) {
            Collections.shuffle(candidates);
            KNNEstimator estimator = new KNNEstimator(candidates.subList(nProbe, candidates.size()), KNN_ESTIMATOR_K);
            for (int j = 0; j < nProbe; ++j) {
                KNNEstimator.DataPoint p = candidates.get(j);
                p.consistencyScr += estimator.estimate(p);
                ++p.nProbeTimes;
            }

            // log progress
            if (session != null) {
                int currentPercent = (int) ((double) (i + 1) * 100 / CONSISTENCY_LEARNING_N_FOLD);
                if (currentPercent > lastPercent) {
                    lastPercent = currentPercent;
                    try {
                        session.getRemote().sendString("{\"rescore-progress\":" + currentPercent + "}");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // interpolate
        for (int i = 0; i < candidates.size(); ++i) {
            KNNEstimator.DataPoint p = candidates.get(i);
            if (p.nProbeTimes > 0) {
                p.si.rescore = p.si.score + INTERPOLATION_WEIGHT * (p.consistencyScr / p.nProbeTimes - p.si.score);
            }
        }
    }
}
