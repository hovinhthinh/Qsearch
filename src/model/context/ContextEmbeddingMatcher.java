package model.context;

import nlp.Glove;
import util.Constants;

import java.util.ArrayList;

public class ContextEmbeddingMatcher implements ContextMatcher {
    private double alpha;

    public ContextEmbeddingMatcher(double alpha) {
        this.alpha = alpha;
    }

    // Lower is better
    // range from 1 -> 2
    public double directedEmbeddingIdfDistance(ArrayList<String> queryX, ArrayList<String> factX) {
        // TODO: Currently not supporting TIME (TIME is computed like normal terms).
        double score = 0;
        double totalIdf = 0;
        for (String qX : queryX) {
            double min = Constants.MAX_DOUBLE;
            for (String fX : factX) {
                double sim = Glove.cosineDistance(qX, fX);
                if (sim != -1) {
                    min = Math.min(min, sim);
                }
            }
            double idf = IDF.getRobertsonIdf(qX);
            score += min * idf;
            totalIdf += idf;
        }
        return score / totalIdf + 1;
    }

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
        return directedEmbeddingIdfDistance(queryContext, factContext) *
                Math.pow(directedEmbeddingIdfDistance(factContext, queryContext), alpha);
    }
}
