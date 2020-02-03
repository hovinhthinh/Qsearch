package model.context;

import nlp.Glove;
import util.Constants;

import java.util.ArrayList;

public class ContextEmbeddingMatcher implements ContextMatcher {
    private double alpha;

    public ContextEmbeddingMatcher(double alpha) {
        this.alpha = alpha;
    }

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

    @Override
    public double match(ArrayList<String> queryContext, ArrayList<String> factContext) {
        return directedEmbeddingIdfDistance(queryContext, factContext) *
                Math.pow(directedEmbeddingIdfDistance(factContext, queryContext), alpha);
    }
}
