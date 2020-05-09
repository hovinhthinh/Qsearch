package server.table.experimental.gui;

import server.table.experimental.QfactLight;

import java.util.ArrayList;

public class ResultInstance {
    public String entity;
    public double score;


    public static class SubInstance {
        public QfactLight qfact;
        public double score;

        public static class ContextMatchTrace {
            String token;
            double score;
            String place; // HEADER, CAPTION, TITLE, (maybe more...)

            public ContextMatchTrace(String token, double score, String place) {
                this.token = token;
                this.score = score;
                this.place = place;
            }
        }

        public ArrayList<ContextMatchTrace> traces;

        public SubInstance(QfactLight qfact, double score, ArrayList<ContextMatchTrace> traces) {
            this.qfact = qfact;
            this.score = score;
            this.traces = traces;
        }
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