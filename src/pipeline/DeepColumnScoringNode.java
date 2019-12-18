package pipeline;

import model.table.Table;
import model.table.link.EntityLink;
import nlp.YagoType;
import pipeline.deep.DeepScoringClient;
import util.Pair;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// Link quantity columns to entity columns, return false if there is no quantity column.
public class DeepColumnScoringNode implements TaggingNode {
    public static final Logger LOGGER = Logger.getLogger(DeepColumnScoringNode.class.getName());
    public static final int MIN_MAX_INFERENCE = 0;
    public static final int TYPE_SET_INFERENCE = 1;

    public static final int JOINT_INFERENCE = 2;
    public static final double JOINT_HOMOGENEITY_WEIGHT = 30; // 30 for gini, 1 for entropy
    public static final int JOINT_MAX_NUM_ITERS = 100;
    public static final int JOINT_MAX_LOCAL_CANDIDATES = 10; // set to -1 to disable this threshold. (-1 means INF)

    private int inferenceMode;
    private DeepScoringClient scoringClient;

    public DeepColumnScoringNode(int inferenceMode) {
        this.inferenceMode = inferenceMode;
        this.scoringClient = new DeepScoringClient();
    }

    public DeepColumnScoringNode() {
        this(JOINT_INFERENCE);
    }

    @Override
    public boolean process(Table table) {
        table.quantityToEntityColumn = new int[table.nColumn];
        Arrays.fill(table.quantityToEntityColumn, -1);

        table.quantityToEntityColumnScore = new double[table.nColumn];
        Arrays.fill(table.quantityToEntityColumnScore, -1.0);

        if (inferenceMode == MIN_MAX_INFERENCE || inferenceMode == TYPE_SET_INFERENCE) {
            return directInference(table);
        } else if (inferenceMode == JOINT_INFERENCE) {
            return jointInference(table);
        } else {
            throw new RuntimeException("Invalid inference mode");
        }
    }

    private class ColumnType {
        HashMap<String, Double> type2Freq = new HashMap<>();
        HashMap<String, Double> type2Itf = new HashMap<>();

        private ArrayList<String> types = null;

        public double getHScore() {
            double hScore = 0;
            for (Map.Entry<String, Double> e : type2Freq.entrySet()) {
//                hScore += e.getValue() * Math.log(e.getValue()); // negated entropy (negative)
                hScore += e.getValue() * e.getValue(); // 1 - gini (0 -> 1)
            }
            return hScore;
        }

        public double getLScore(String quantityDesc) {
            if (types == null) {
                types = new ArrayList<>(type2Freq.keySet());
            }
            ArrayList<Double> scores = scoringClient.getScores(types, quantityDesc);
            double lScore = 0;
            for (int j = 0; j < types.size(); ++j) {
                String t = types.get(j);
                lScore += scores.get(j) * type2Itf.get(t) * type2Freq.get(t);
            }
            return lScore;
        }
    }

    private class BacktrackJointInferenceInfo {
        int[] currentColumnLinking;
        double[] currentColumnLinkingScore;
        String[][] currentEntityAssignment;
        double[] currentHomogeneityScore;
        double currentJointScore;
        String[][] savedEntityAssignment; // For saving each best local assignment

        int[] bestColumnLinking;
        double[] bestColumnLinkingScore;
        String[][] bestEntityAssignment;
        double bestJointScore;

        Table table;

        public BacktrackJointInferenceInfo(Table t) {
            table = t;
            currentColumnLinking = new int[table.nColumn];
            currentColumnLinkingScore = new double[table.nColumn];
            savedEntityAssignment = new String[table.nDataRow][table.nColumn];
            currentEntityAssignment = new String[table.nDataRow][table.nColumn];
            currentHomogeneityScore = new double[table.nColumn];
            currentJointScore = 0;

            // for saving
            bestColumnLinking = new int[table.nColumn];
            bestColumnLinkingScore = new double[table.nColumn];
            bestEntityAssignment = new String[table.nDataRow][table.nColumn];
            bestJointScore = Double.NEGATIVE_INFINITY;
            Arrays.fill(bestColumnLinking, -1);
            Arrays.fill(bestColumnLinkingScore, -1.0);
        }

        public void captureCurrentColumnLinking() {
            for (int i = 0; i < bestColumnLinking.length; ++i) {
                bestColumnLinking[i] = currentColumnLinking[i];
                bestColumnLinkingScore[i] = currentColumnLinkingScore[i];
            }
            for (int i = 0; i < bestEntityAssignment.length; ++i) {
                for (int j = 0; j < bestEntityAssignment[i].length; ++j) {
                    bestEntityAssignment[i][j] = currentEntityAssignment[i][j];
                }
            }
            bestJointScore = currentJointScore;
        }


        private ColumnType[] buildColumnTypeSetForCurrentAssignment() {
            ColumnType[] columnTypeSet = new ColumnType[table.nColumn];
            for (int i = 0; i < table.nColumn; ++i) {
                columnTypeSet[i] = buildColumnTypeForCurrentAssignment(i);
            }
            return columnTypeSet;
        }

        private ColumnType buildColumnTypeForCurrentAssignment(int eCol) {
            if (!table.isEntityColumn[eCol]) {
                return null;
            }
            ColumnType ct = new ColumnType();
            for (int i = 0; i < table.nDataRow; ++i) {
                String e = currentEntityAssignment[i][eCol];
                if (e == null) {
                    continue;
                }
                List<Pair<String, Double>> types = YagoType.getTypes(e, true);
                for (Pair<String, Double> p : types) {
                    ct.type2Itf.putIfAbsent(p.first, p.second);
                    ct.type2Freq.put(p.first, ct.type2Freq.getOrDefault(p.first, 0.0) + 1.0 / types.size());
                }
            }
            // Normalize freq.
            double totalFreq = ct.type2Freq.values().stream().mapToDouble(o -> o.doubleValue()).sum();
            for (String t : ct.type2Itf.keySet()) {
                ct.type2Freq.put(t, ct.type2Freq.get(t) / totalFreq);
            }
            return ct;
        }


        public void recomputeBasedOnCurrentAssignment() {
            // Compute currentColumnLinkingScore, currentJointScore
            ColumnType[] columnTypeSet = buildColumnTypeSetForCurrentAssignment();

            double homogeneity = 0;
            double connectivity = 0;
            int nEntityColumns = 0, nQuantityColums = 0;
            for (int i = 0; i < table.nColumn; ++i) {
                if (table.isEntityColumn[i]) {
                    // homogeneity
                    ++nEntityColumns;
                    homogeneity += (currentHomogeneityScore[i] = columnTypeSet[i].getHScore());
                } else if (table.isNumericColumn[i]) {
                    // connectivity
                    ++nQuantityColums;
                    ColumnType ct = columnTypeSet[currentColumnLinking[i]];
                    // (1) combined quantity header
                    double lScore = ct.getLScore(table.getCombinedHeader(i));
                    // (2) last quantity header
                    if (table.nHeaderRow > 1) {
                        lScore = Math.max(lScore, ct.getLScore(table.header[table.nHeaderRow - 1][i].text));
                    }
                    connectivity += (currentColumnLinkingScore[i] = lScore);
                }
            }
            homogeneity /= nEntityColumns;
            connectivity /= nQuantityColums;
            // joint score
            currentJointScore = connectivity + JOINT_HOMOGENEITY_WEIGHT * homogeneity;
        }

        public double newScoreOfLocalAssignment(int row, int col, String candidate) {
            // try new candidate
            String oldCandidate = currentEntityAssignment[row][col];
            currentEntityAssignment[row][col] = candidate;
            // now compute

            // partial built column type set
            ColumnType[] columnTypeSet = new ColumnType[table.nColumn];

            double homogeneity = 0;
            double connectivity = 0;
            int nEntityColumns = 0, nQuantityColums = 0;
            for (int i = 0; i < table.nColumn; ++i) {
                if (table.isEntityColumn[i]) {
                    // homogeneity
                    ++nEntityColumns;
                    if (i != col) {
                        homogeneity += currentHomogeneityScore[i];
                    } else {
                        if (columnTypeSet[i] == null) {
                            columnTypeSet[i] = buildColumnTypeForCurrentAssignment(i);
                        }
                        homogeneity += columnTypeSet[i].getHScore();
                    }
                } else if (table.isNumericColumn[i]) {
                    // connectivity
                    ++nQuantityColums;
                    if (currentColumnLinking[i] != col) {
                        connectivity += currentColumnLinkingScore[i];
                    } else {
                        if (columnTypeSet[currentColumnLinking[i]] == null) {
                            columnTypeSet[currentColumnLinking[i]] = buildColumnTypeForCurrentAssignment(currentColumnLinking[i]);
                        }
                        ColumnType ct = columnTypeSet[currentColumnLinking[i]];
                        // (1) combined quantity header
                        double lScore = ct.getLScore(table.getCombinedHeader(i));
                        // (2) last quantity header
                        if (table.nHeaderRow > 1) {
                            lScore = Math.max(lScore, ct.getLScore(table.header[table.nHeaderRow - 1][i].text));
                        }
                        connectivity += lScore;
                    }
                }
            }
            homogeneity /= nEntityColumns;
            connectivity /= nQuantityColums;

            // restore old candidate
            currentEntityAssignment[row][col] = oldCandidate;

            // joint score
            return connectivity + JOINT_HOMOGENEITY_WEIGHT * homogeneity;
        }
    }

    private void backtrackJointInference(Table table, BacktrackJointInferenceInfo info, int currentCol) {
        // backtracking all possible column linking
        if (currentCol < table.nColumn) {
            if (!table.isNumericColumn[currentCol]) {
                backtrackJointInference(table, info, currentCol + 1);
            } else {
                for (int i = 0; i < table.nColumn; ++i) {
                    if (!table.isEntityColumn[i]) {
                        continue;
                    }
                    info.currentColumnLinking[currentCol] = i;
                    backtrackJointInference(table, info, currentCol + 1);
                }
            }
            return;
        }

        // Now process the ICA algorithm;
        // Init candidates
        for (int j = 0; j < table.nColumn; ++j) {
            if (!table.isEntityColumn[j]) {
                continue;
            }
            for (int i = 0; i < table.nDataRow; ++i) {
                EntityLink el = table.data[i][j].getRepresentativeEntityLink();
                if (el != null) {
                    info.currentEntityAssignment[i][j] = info.savedEntityAssignment[i][j] = el.candidates.get(0).first;
                }
            }
        }
        info.recomputeBasedOnCurrentAssignment();

        // Iterative classifying
        int nIterations = 0;
        boolean hasChange;
        do {
            hasChange = false;
            for (int i = 0; i < table.nDataRow; ++i) {
                for (int j = 0; j < table.nColumn; ++j) {
                    if (info.currentEntityAssignment[i][j] == null) {
                        continue;
                    }
                    double currentLocalScore = info.currentJointScore;
                    int nTried = 0;
                    for (Pair<String, Integer> c : table.data[i][j].getRepresentativeEntityLink().candidates) {
                        if (JOINT_MAX_LOCAL_CANDIDATES >= 0 && ++nTried > JOINT_MAX_LOCAL_CANDIDATES) {
                            break;
                        }
                        if (c.first.equals(info.currentEntityAssignment[i][j])) {
                            continue;
                        }
                        double newLocalScore = info.newScoreOfLocalAssignment(i, j, c.first);
                        if (newLocalScore > currentLocalScore) {
                            currentLocalScore = newLocalScore;
                            info.savedEntityAssignment[i][j] = c.first;
                            hasChange = true;
                        }
                    }
                }
            }
            if (hasChange) {
                for (int i = 0; i < table.nDataRow; ++i) {
                    for (int j = 0; j < table.nColumn; ++j) {
                        info.currentEntityAssignment[i][j] = info.savedEntityAssignment[i][j];
                    }
                }
                info.recomputeBasedOnCurrentAssignment();
            }
        } while (++nIterations < JOINT_MAX_NUM_ITERS && hasChange);
        // Compare with best assignment
        if (info.currentJointScore > info.bestJointScore) {
            info.captureCurrentColumnLinking();
        }
    }

    private boolean jointInference(Table table) {
        BacktrackJointInferenceInfo info = new BacktrackJointInferenceInfo(table);
        backtrackJointInference(table, info, 0);
        // set candidates back to tables
        for (int i = 0; i < table.nDataRow; ++i) {
            for (int j = 0; j < table.nColumn; ++j) {
                // remove all candidates of entity links to reduce size
                // (only in the body - because the header is not tagged).
                for (EntityLink el : table.data[i][j].entityLinks) {
                    el.candidates = null;
                }
                EntityLink el = table.data[i][j].getRepresentativeEntityLink();
                if (el != null && info.bestEntityAssignment[i][j] != null) {
                    el.target = "YAGO:" + info.bestEntityAssignment[i][j].substring(1, info.bestEntityAssignment[i][j].length() - 1);
                }
            }
        }
        // set column linking
        for (int i = 0; i < table.nColumn; ++i) {
            table.quantityToEntityColumn[i] = info.bestColumnLinking[i];
            table.quantityToEntityColumnScore[i] = info.bestColumnLinkingScore[i];
        }

        return true;
    }

    @Deprecated
    public boolean directInference(Table table) {
        boolean result = false;
        // loop for quantity columns.

        double[] keyColumnScores = new double[table.nColumn];

        int nNumericCols = 0;
        for (int pivotCol = 0; pivotCol < table.nColumn; ++pivotCol) {
            if (!table.isNumericColumn[pivotCol]) {
                continue;
            }
            ++nNumericCols;
            int targetCol = -1;
            double linkingConf = -1;
            // loop for entity columns.
            for (int col = 0; col < table.nColumn; ++col) {
                if (!table.isEntityColumn[col]) {
                    continue;
                }

                double totalConf = -1;
                if (inferenceMode == MIN_MAX_INFERENCE) {
                    totalConf = inferMinMax(table, pivotCol, col);
                } else if (inferenceMode == TYPE_SET_INFERENCE) {
                    totalConf = inferTypeSet(table, pivotCol, col);
                } else {
                    throw new RuntimeException("Not implemented");
                }
                if (targetCol == -1 || totalConf > linkingConf) {
                    targetCol = col;
                    linkingConf = totalConf;
                    result = true;
                }
                keyColumnScores[col] += totalConf;
            }

            table.quantityToEntityColumn[pivotCol] = targetCol;
            table.quantityToEntityColumnScore[pivotCol] = linkingConf;
        }

        for (int col = 0; col < table.nColumn; ++col) {
            if (!table.isEntityColumn[col]) {
                continue;
            }
            if (nNumericCols > 0) {
                keyColumnScores[col] /= nNumericCols;
            }
            if (table.keyColumn == -1 || keyColumnScores[col] > table.keyColumnScore) {
                table.keyColumn = col;
                table.keyColumnScore = keyColumnScores[col];
            }
        }
        return result;
    }

    @Deprecated
    private double inferMinMax(Table table, int qCol, int eCol) {
        // header conf: max from combined and last cell only.
        double headerLinkingConf = scoringClient.getScore(table.getCombinedHeader(eCol), table.getCombinedHeader(qCol));
        if (table.nHeaderRow > 1) {
            headerLinkingConf = Math.max(
                    headerLinkingConf,
                    scoringClient.getScore(table.header[table.nHeaderRow - 1][eCol].text, table.header[table.nHeaderRow - 1][qCol].text));
        }
        // entity conf: min from each detected entity.
        double entityLinkingConf = -1;
        for (int i = 0; i < table.nDataRow; ++i) {
            EntityLink e = table.data[i][eCol].getRepresentativeEntityLink();
            if (e == null) {
                continue;
            }
            List<String> types = YagoType.getTypes("<" + e.target.substring(e.target.lastIndexOf(":") + 1) + ">", true)
                    .stream().map(o -> o.first).collect(Collectors.toList());
            if (types == null) {
                continue;
            }
            ArrayList<Double> scrs = scoringClient.getScores(types, table.getCombinedHeader(qCol));
            if (table.nHeaderRow > 1) {
                scrs.addAll(scoringClient.getScores(types, table.header[table.nHeaderRow - 1][qCol].text));
            }
            if (scrs.isEmpty()) {
                continue;
            }
            double score = Collections.max(scrs);
            entityLinkingConf = (entityLinkingConf == -1 ? score : Math.min(entityLinkingConf, score));
        }

        return Math.max(headerLinkingConf, entityLinkingConf);
    }

    @Deprecated
    // sum(freq(t) * itf(t) * dist(t,q))
    private double inferTypeSet(Table table, int qCol, int eCol) {
        HashMap<String, Double> type2Freq = new HashMap<>();
        HashMap<String, Double> type2Itf = new HashMap<>();

        for (int i = 0; i < table.nDataRow; ++i) {
            EntityLink e = table.data[i][eCol].getRepresentativeEntityLink();
            if (e == null) {
                continue;
            }
            List<Pair<String, Double>> types = YagoType.getTypes("<" + e.target.substring(e.target.lastIndexOf(":") + 1) + ">", true);
            for (Pair<String, Double> p : types) {
                type2Itf.putIfAbsent(p.first, p.second);
                type2Freq.put(p.first, type2Freq.getOrDefault(p.first, 0.0) + 1.0 / types.size());
            }
        }
        // Normalize freq.
        double totalFreq = type2Freq.values().stream().mapToDouble(o -> o.doubleValue()).sum();
        for (String t : type2Itf.keySet()) {
            type2Freq.put(t, type2Freq.get(t) / totalFreq);
        }

        ArrayList<String> types = new ArrayList<>(type2Freq.keySet());

        // combined quantity header
        ArrayList<Double> scrs = scoringClient.getScores(types, table.getCombinedHeader(qCol));
        double entityLinkingConf = 0;
        for (int i = 0; i < types.size(); ++i) {
            String t = types.get(i);
            entityLinkingConf += scrs.get(i) * type2Itf.get(t) * type2Freq.get(t);
        }
        // last quantity header
        if (table.nHeaderRow > 1) {
            scrs = scoringClient.getScores(types, table.header[table.nHeaderRow - 1][qCol].text);
            double entityLinkingConfLastHeader = 0;
            for (int i = 0; i < types.size(); ++i) {
                String t = types.get(i);
                entityLinkingConfLastHeader += scrs.get(i) * type2Itf.get(t) * type2Freq.get(t);
            }
            entityLinkingConf = Math.max(entityLinkingConf, entityLinkingConfLastHeader);
        }
        return entityLinkingConf;
    }
}
