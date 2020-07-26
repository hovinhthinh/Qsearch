package server.table;

import java.util.ArrayList;

public class ResultInstance {
    public String entity;
    public double score;

    public String eval; // For evaluation

    public static class SubInstance {
        public QfactLight qfact;
        public double score;

        // EXPERIMENTAL
        public double rescore; // this is after applying consistency-based rescoring

        public static class ContextMatchTrace {
            public String token;
            public double score;
            public String place; // HEADER, CAPTION, TITLE, (maybe more...)

            public ContextMatchTrace(String token, double score, String place) {
                this.token = token;
                this.score = score;
                this.place = place;
            }
        }

        public String quantityConvertedStr;
        public double quantityStandardValue;
        public ArrayList<ContextMatchTrace> traces;

        public SubInstance(QfactLight qfact, double score, double quantityStandardValue, String quantityConvertedStr, ArrayList<ContextMatchTrace> traces) {
            this.qfact = qfact;
            this.score = score;
            this.quantityStandardValue = quantityStandardValue;
            this.quantityConvertedStr = quantityConvertedStr;
            this.traces = traces;
        }
    }

    public ArrayList<SubInstance> subInstances = new ArrayList<>();

    public void addSubInstance(SubInstance si) {
        score = Math.max(score, si.score);
        // check if there is a better fact from same table;
        int sameIdPivot = -1;
        for (int i = 0; i < subInstances.size(); ++i) {
            if (subInstances.get(i).qfact.tableId.equals(si.qfact.tableId)) {
                sameIdPivot = i;
                break;
            }
        }
        if (sameIdPivot != -1) {
            if (subInstances.get(sameIdPivot).score < si.score) {
                subInstances.set(sameIdPivot, si);
            }
            return;
        }

        subInstances.add(si);
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