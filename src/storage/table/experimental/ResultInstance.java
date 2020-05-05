package storage.table.experimental;

import java.util.ArrayList;

@Deprecated
public class ResultInstance {
    public String entity;
    public double score;

    public static class SubInstance {
        public double score;
        public String quantity;
        public String context;
        public String domain;
        public String source;
    }

    public ArrayList<SubInstance> subInstances = new ArrayList<>();

    public void addSubInstance(SubInstance si) {
        subInstances.add(si);
        score = Math.max(score, si.score);
        if (subInstances.size() > 5) {
            int pivot = 0;
            for (int i = 1; i < subInstances.size(); ++i) {
                if (subInstances.get(i).score < subInstances.get(pivot).score) {
                    pivot = i;
                }
            }
            subInstances.set(pivot, subInstances.get(subInstances.size() - 1));
            subInstances.remove(subInstances.size() - 1);
        }
    }
}