package server.text;

import util.Constants;

import java.util.ArrayList;

public class ResultInstance implements Comparable<ResultInstance> {
    public static transient final int TOP_KEEP_SUBINSTANCES = 5;

    public String entity;
    public int popularity; // based on wikipedia page view

    public double score = Constants.MAX_DOUBLE;

    public String eval; // For evaluation

    public transient int topKeepSubInstances;

    public ResultInstance(int topKeepSubInstances) {
        this.topKeepSubInstances = topKeepSubInstances;
    }

    public ResultInstance() {
        this(TOP_KEEP_SUBINSTANCES);
    }

    public static class SubInstance {
        public String kbcId;
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
    }

    public void addSubInstance(SubInstance si) {
        score = Math.min(score, si.score);

        subInstances.add(si);
        if (subInstances.size() > this.topKeepSubInstances) {
            int pivot = 0;
            for (int i = 1; i < subInstances.size(); ++i) {
                if (subInstances.get(i).score > subInstances.get(pivot).score) {
                    pivot = i;
                }
            }
            subInstances.set(pivot, subInstances.get(subInstances.size() - 1));
            subInstances.remove(subInstances.size() - 1);
        }
    }

    public ArrayList<SubInstance> subInstances = new ArrayList<>();

    @Override
    public int compareTo(ResultInstance o) {
        if (Math.abs(this.score - o.score) > 1e-6) {
            return Double.compare(this.score, o.score);
        }
        // Entities with same score are ordered by estimated popularity.
        return Integer.compare(o.popularity, this.popularity);
    }
}
