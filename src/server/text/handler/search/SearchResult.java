package server.text.handler.search;

import model.quantity.QuantityConstraint;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;

public class SearchResult {
    public String verdict; // "OK" is good, otherwise the error message.
    public String fullQuery; // optional
    public String typeConstraint;
    public String contextConstraint;
    public QuantityConstraint quantityConstraint;
    public String evalDomain; // For evaluation
    public String matchingModel;
    public int numResults;
    public ArrayList<ResultInstance> topResults = new ArrayList<>();

    public String encode() {
//        return DigestUtils.md5Hex(typeConstraint + "\t" + contextConstraint + "\t" + quantityConstraint.toString());
        return evalDomain + "_" + (typeConstraint.replace(' ', '-')
                + "_" + contextConstraint.replace(' ', '-')
                + "_" + quantityConstraint.phrase.replace(' ', '-')).replace('/', '-');
    }

    public static class ResultInstance implements Comparable<ResultInstance> {
        public String entity;
        public int estimatedPopularity; // this is estimated based on the number of Qfacts extracted, not the real popularity.

        public double score;
        public String quantity;
        public double quantityStandardValue;
        public String sentence;
        public String source;

        // For verbose
        public String entityStr;
        public String quantityStr;
        public ArrayList<String> contextStr;

        public String quantityConvertedStr;

        public String eval; // For evaluation

        @Override
        public int compareTo(ResultInstance o) {
            if (Math.abs(this.score - o.score) > 1e-6) {
                return Double.compare(this.score, o.score);
            }
            // Entities with same score are ordered by estimated popularity.
            return Integer.compare(o.estimatedPopularity, this.estimatedPopularity);
        }
    }

    // below are recall-based metrics computed in case groundtruth is provided.
    public Double RR, AP, RECALL, RECALL_10;
}
