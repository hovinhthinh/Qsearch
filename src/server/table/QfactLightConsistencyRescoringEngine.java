package server.table;

import model.context.IDF;
import model.quantity.Quantity;
import nlp.Glove;
import nlp.NLP;
import org.eclipse.jetty.websocket.api.Session;
import storage.table.index.TableIndex;
import storage.table.index.TableIndexStorage;
import uk.ac.susx.informatics.Morpha;
import util.Pair;
import util.Vectors;
import util.headword.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

class KNNEstimator {
    public List<DataPoint> training;
    public int k;

    @Deprecated
    private double quantityMeanNormalizeValue;

    private double quantityFeatureBoost;

    public static class DataPoint {
        public ResultInstance.SubInstance si;
        public HashMap<String, Double> termTfidfMap;
        public double consistencyScr;
        public int nProbeTimes;

        public double[] vector;

        private Quantity siQuantity = null;

        private Quantity getQuantity() {
            if (siQuantity != null) {
                return siQuantity;
            }
            return siQuantity = Quantity.fromQuantityString(si.qfact.quantity);
        }

        public DataPoint(ResultInstance.SubInstance si, HashMap<String, Double> termTfidfMap) {
            this.si = si;
            this.termTfidfMap = termTfidfMap;
            this.consistencyScr = 0;
            this.nProbeTimes = 0;

            // init vector
            this.vector = new double[Glove.DIM];

            double sumIdf = 0;
            for (Map.Entry<String, Double> e : termTfidfMap.entrySet()) {
                double[] emb = Glove.getEmbedding(e.getKey());
                if (emb == null) {
                    continue;
                }
                sumIdf += e.getValue();
                this.vector = Vectors.sum(this.vector, Vectors.multiply(emb, e.getValue()));
            }
            if (sumIdf != 0) {
                this.vector = Vectors.multiply(this.vector, 1 / sumIdf);
            }
        }

        // using tf-idf Vector Space Model here, computing Cosine similarity
        @Deprecated
        public double distOld(DataPoint o, double quantityMeanValue) {
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
            Quantity q = getQuantity();
            Quantity oQ = o.getQuantity();

            double qValue = q.value * q.getScale() / quantityMeanValue * QfactLightConsistencyRescoringEngine.QUANTITY_FEATURE_BOOST;
            double oQValue = oQ.value * oQ.getScale() / quantityMeanValue * QfactLightConsistencyRescoringEngine.QUANTITY_FEATURE_BOOST;
            dotProd += qValue * oQValue;
            length += qValue * qValue;
            oLength += oQValue * oQValue;

            return 0.5 - dotProd / Math.sqrt(length) / Math.sqrt(oLength) / 2;
        }

        // using Embedding Vector Space Model here, computing Cosine similarity
        public double dist(DataPoint o, double quantityFeatureBoost) {
            double termDist = Vectors.cosineD(vector, o.vector);

            // TODO: Alternative: compute log version of quantity values, that would make the relative distance less sensitive for bigger numbers.
            // quantity values
            Quantity q = getQuantity();
            Quantity oQ = o.getQuantity();

            double qValue = q.value * q.getScale();
            double oQValue = oQ.value * oQ.getScale();

            double quantityRelDist = Math.min(Math.abs(qValue - oQValue) / Math.max(Math.abs(qValue), Math.abs(oQValue)), 1);
            return (1 - quantityFeatureBoost) * termDist + quantityFeatureBoost * quantityRelDist;
        }
    }

    public KNNEstimator(List<DataPoint> training, int k, double quantityFeatureBoost) {
        this.training = training;
        this.k = k;
        this.quantityFeatureBoost = quantityFeatureBoost;
        this.quantityMeanNormalizeValue = 0;
        for (DataPoint p : training) {
            Quantity q = Quantity.fromQuantityString(p.si.qfact.quantity);
            this.quantityMeanNormalizeValue += Math.abs(q.value * q.getScale()) / training.size();
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
            Pair<Double, DataPoint> dist2Point = new Pair<>(p.dist(t, quantityFeatureBoost), t);

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
    public static double HEADER_TF_WEIGHT = 1;
    public static double CAPTION_TF_WEIGHT = 0.9;
    public static double TITLE_TF_WEIGHT = 0.3;
    public static double DOM_HEADING_TF_WEIGHT = 0.3;
    public static double SAME_ROW_TF_WEIGHT = 0.1;
    public static double RELATED_TEXT_TF_WEIGHT = 0.1;

    public static double QUANTITY_FEATURE_BOOST = 0.1;

    // params for consistency learning
    public static int CONSISTENCY_LEARNING_N_FOLD = 200;
    public static double CONSISTENCY_LEARNING_PROBE_RATE = 0.3;
    public static int KNN_ESTIMATOR_K = 3;
    public static double INTERPOLATION_WEIGHT = 0.3;

    private static HashMap<String, Double> qfactLight2TermTfidfMap(QfactLight f, TableIndex ti, Map weightMap) {
        HashMap<String, Double> termTfidfMap = new HashMap<>();

        if (weightMap == null) {
            weightMap = new HashMap<>();
        }

        // HEADER
        for (String fX : NLP.splitSentence(f.headerContext.toLowerCase())) {
            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                continue;
            }
            fX = StringUtils.stem(fX, Morpha.any);
            termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + (double) weightMap.getOrDefault("HEADER_TF_WEIGHT", HEADER_TF_WEIGHT));
        }
        // CAPTION
        for (String fX : NLP.splitSentence(ti.caption.toLowerCase())) {
            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                continue;
            }
            fX = StringUtils.stem(fX, Morpha.any);
            termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + (double) weightMap.getOrDefault("CAPTION_TF_WEIGHT", CAPTION_TF_WEIGHT));
        }
        // TITLE
        for (String fX : NLP.splitSentence(ti.pageTitle)) {
            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                continue;
            }
            fX = StringUtils.stem(fX, Morpha.any);
            termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + (double) weightMap.getOrDefault("TITLE_TF_WEIGHT", TITLE_TF_WEIGHT));
        }
        // DOM HEADINGs
        for (String fX : NLP.splitSentence(ti.sectionTitles)) {
            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                continue;
            }
            fX = StringUtils.stem(fX, Morpha.any);
            termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + (double) weightMap.getOrDefault("DOM_HEADING_TF_WEIGHT", DOM_HEADING_TF_WEIGHT));
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
                termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + (double) weightMap.getOrDefault("SAME_ROW_TF_WEIGHT", SAME_ROW_TF_WEIGHT));
            }
        }
        // PAGE CONTENT
        for (String fX : NLP.splitSentence(ti.pageContent.toLowerCase())) {
            if (NLP.BLOCKED_STOPWORDS.contains(fX) || NLP.BLOCKED_SPECIAL_CONTEXT_CHARS.contains(fX)) {
                continue;
            }
            fX = StringUtils.stem(fX, Morpha.any);
            termTfidfMap.put(fX, termTfidfMap.getOrDefault(fX, 0.0) + (double) weightMap.getOrDefault("RELATED_TEXT_TF_WEIGHT", RELATED_TEXT_TF_WEIGHT));
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

    public static void consistencyBasedRescore(ArrayList<ResultInstance.SubInstance> priorScoredQfacts, Map params, Session session) {
        if (params == null) {
            params = new HashMap<>();
        }
        ArrayList<KNNEstimator.DataPoint> candidates = new ArrayList<>();

        for (int i = 0; i < priorScoredQfacts.size(); ++i) {
            ResultInstance.SubInstance si = priorScoredQfacts.get(i);
            si.rescore = si.score;
            candidates.add(new KNNEstimator.DataPoint(si, qfactLight2TermTfidfMap(si.qfact, TableIndexStorage.get(si.qfact.tableId), params)));
        }

        // Now perform consistency-based re-scoring.
        int nProbe = (int) (candidates.size() * (double) params.getOrDefault("CONSISTENCY_LEARNING_PROBE_RATE", CONSISTENCY_LEARNING_PROBE_RATE) + 1e-6);
        if (nProbe == 0) {
            return;
        }

        int kNN_k = (int) params.getOrDefault("KNN_ESTIMATOR_K", KNN_ESTIMATOR_K);
        int nFold = (int) params.getOrDefault("CONSISTENCY_LEARNING_N_FOLD", CONSISTENCY_LEARNING_N_FOLD);
        double qBoost = (double) params.getOrDefault("QUANTITY_FEATURE_BOOST", QUANTITY_FEATURE_BOOST);
        int lastPercent = 0;

        for (int i = 0; i < nFold; ++i) {
            Collections.shuffle(candidates);
            KNNEstimator estimator = new KNNEstimator(candidates.subList(nProbe, candidates.size()), kNN_k, qBoost);
            for (int j = 0; j < nProbe; ++j) {
                KNNEstimator.DataPoint p = candidates.get(j);
                p.consistencyScr += estimator.estimate(p);
                ++p.nProbeTimes;
            }

            // log progress
            if (session != null) {
                int currentPercent = (int) ((double) (i + 1) * 100 / nFold);
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
                p.si.rescore = p.si.score + (double) params.getOrDefault("INTERPOLATION_WEIGHT", INTERPOLATION_WEIGHT) * (p.consistencyScr / p.nProbeTimes - p.si.score);
            }
        }
    }
}
