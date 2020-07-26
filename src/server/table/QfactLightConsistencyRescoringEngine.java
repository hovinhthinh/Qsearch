package server.table;

import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import model.context.IDF;
import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import nlp.NLP;
import storage.table.index.TableIndex;
import storage.table.index.TableIndexStorage;
import uk.ac.susx.informatics.Morpha;
import util.Pair;
import util.headword.StringUtils;

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

            double qValue = q.value * QuantityDomain.getScale(q) / quantityMeanValue;
            double oQValue = oQ.value * QuantityDomain.getScale(oQ) / quantityMeanValue;
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
        ObjectHeapPriorityQueue<Pair<Double, DataPoint>> queue
                = new ObjectHeapPriorityQueue<>((a, b) -> b.first.compareTo(a.first));

        // kNN
        for (DataPoint t : training) {
            if (p.si.qfact.tableId.equals(t.si.qfact.tableId)) {
                continue;
            }
            queue.enqueue(new Pair<>(p.dist(t, quantityMeanNormalizeValue), t));
            if (queue.size() > k) {
                queue.dequeue();
            }
        }

        if (queue.size() == 0) {
            return p.si.score;
        }

        double res = 0;
        int size = queue.size();
        while (!queue.isEmpty()) {
            res += queue.dequeue().second.si.score / size;
        }
        return res;
    }
}

public class QfactLightConsistencyRescoringEngine {
    public static final Logger LOGGER = Logger.getLogger(QfactLightConsistencyRescoringEngine.class.getName());

    // TODO: adjust these params.
    public static double HEADER_TF_WEIGHT = 1;
    public static double CAPTION_TF_WEIGHT = 0.9;
    public static double TITLE_TF_WEIGHT = 0.85;
    public static double SAME_ROW_TF_WEIGHT = 0.85;
    public static double RELATED_TEXT_TF_WEIGHT = 0;

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

        // normalize tf, and multiply with idf
        termTfidfMap.replaceAll((k, v) -> Math.log(1 + v) * IDF.getRobertsonIdf(k));
        return termTfidfMap;
    }

    public static int CONSISTENCY_LEARNING_N_FOLD = 100;
    public static double CONSISTENCY_LEARNING_PROBE_RATE = 0.2;
    public static int KNN_ESTIMATOR_K = 5;
    public static double INTERPOLATION_WEIGHT = 0.2;

    public static void consistencyBasedRescore(ArrayList<ResultInstance.SubInstance> priorScoredQfacts) {
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
        for (int i = 0; i < CONSISTENCY_LEARNING_N_FOLD; ++i) {
            Collections.shuffle(candidates);
            KNNEstimator estimator = new KNNEstimator(candidates.subList(nProbe, candidates.size()), KNN_ESTIMATOR_K);
            for (int j = 0; j < nProbe; ++j) {
                KNNEstimator.DataPoint p = candidates.get(j);
                p.consistencyScr += estimator.estimate(p);
                ++p.nProbeTimes;
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
