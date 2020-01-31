package pipeline;

import model.table.Table;
import model.table.link.EntityLink;
import nlp.YagoType;
import org.apache.commons.collections4.map.LRUMap;
import pipeline.deep.DeepScoringClient;
import pipeline.deep.ScoringClientInterface;
import util.Constants;
import util.Pair;

import java.util.*;
import java.util.logging.Logger;

// Link quantity columns to entity columns, return false if there is no quantity column.
public class DeepColumnScoringNode implements TaggingNode {
    public static final Logger LOGGER = Logger.getLogger(DeepColumnScoringNode.class.getName());

    private static final String SEPARATOR = "\t";
    private static final int CACHE_SIZE = 1000000;

    public static final int PRIOR_INFERENCE = 1;
    public static final int JOINT_INFERENCE = 2;

    // TODO: fix this weight
    public static final double DEFAULT_JOINT_HOMOGENEITY_WEIGHT = 30; // 30 for gini, 1 for entropy; Constants.MAX_DOUBLE (set the connectivity to 0)

    public static final int JOINT_MAX_NUM_ITERS = 100;
    public static final int JOINT_MAX_LOCAL_CANDIDATES = 10; // set to -1 to disable this threshold. (-1 means INF)
    public static final int JOINT_MAX_NUM_COLUMN_LINKING = 100; // to prune too large tables. (-1 means INF)

    // TODO: fix this weight
    public static final double FIRST_COLUMN_WEIGHT = 0;

    private int inferenceMode;
    private ScoringClientInterface scoringClient;
    private LRUMap<String, Double> cache = new LRUMap<>(CACHE_SIZE);
    private double homogeneityWeight;

    public DeepColumnScoringNode(int inferenceMode, ScoringClientInterface scoringClient, double homogeneityWeight) {
        this.inferenceMode = inferenceMode;
        this.scoringClient = scoringClient;
        this.homogeneityWeight = homogeneityWeight;
    }

    public DeepColumnScoringNode(int inferenceMode, ScoringClientInterface scoringClient) {
        this(inferenceMode, scoringClient, DEFAULT_JOINT_HOMOGENEITY_WEIGHT);
    }

    public DeepColumnScoringNode(int inferenceMode) {
        this.inferenceMode = inferenceMode;
        this.scoringClient = new DeepScoringClient(false, -1);
    }

    // With cache
    private ArrayList<Double> getScores(List<String> entitiesDesc, String quantityDesc) {
        if (entitiesDesc.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Double> results = new ArrayList<>();

        List<String> newEntitiesDesc = new ArrayList<>();
        for (int i = 0; i < entitiesDesc.size(); ++i) {
            String key = String.format("%s%s%s", entitiesDesc.get(i), SEPARATOR, quantityDesc);
            if (cache.containsKey(key)) {
                results.add(cache.get(key));
                continue;
            }
            results.add(null);
            newEntitiesDesc.add(entitiesDesc.get(i));
        }
        if (newEntitiesDesc.size() == 0) {
            return results;
        }

        ArrayList<Double> resultsFromPythonClient = scoringClient.getScores(newEntitiesDesc, quantityDesc);
        int cur = 0;
        for (int i = 0; i < results.size(); ++i) {
            if (results.get(i) == null) {
                results.set(i, resultsFromPythonClient.get(cur++));
                cache.put(String.format("%s%s%s", entitiesDesc.get(i), SEPARATOR, quantityDesc), results.get(i));
            }
        }
        return results;
    }

    // With cache
    private double getScore(String typeDesc, String quantityDesc) {
        String key = String.format("%s%s%s", typeDesc, SEPARATOR, quantityDesc);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        double value = scoringClient.getScore(typeDesc, quantityDesc);
        cache.put(key, value);
        return value;
    }

    @Override
    public boolean process(Table table) {
        table.quantityToEntityColumn = new int[table.nColumn];
        Arrays.fill(table.quantityToEntityColumn, -1);

        table.quantityToEntityColumnScore = new double[table.nColumn];
        Arrays.fill(table.quantityToEntityColumnScore, -1.0);

        if (inferenceMode == PRIOR_INFERENCE || inferenceMode == JOINT_INFERENCE) {
            return jointInference(table);
        } else {
            throw new RuntimeException("Invalid inference mode");
        }
    }

    private class ColumnType {
        HashMap<String, Double> type2Itf = new HashMap<>();
        ArrayList<LinkedHashSet<String>> typeSets = new ArrayList<>();

        private ArrayList<String> types = null;

        public double getHScore() {
            // there is only 1 entity in the entity column; check <= 1 for safety
            if (typeSets.size() <= 1) {
                return 0;
            }

            double hScore = 0;
            for (int i = 0; i < typeSets.size(); ++i) {
                LinkedHashSet<String> typeI = typeSets.get(i);
                loop:
                for (int j = i + 1; j < typeSets.size(); ++j) {
                    for (String t : typeSets.get(j)) {
                        if (typeI.contains(t)) {
                            hScore += type2Itf.get(t);
                            continue loop;
                        }
                    }
                }
            }
            return hScore / (typeSets.size() * (typeSets.size() - 1) / 2);
        }

        public double getLScore(String quantityDesc) {
            if (types == null) {
                types = new ArrayList<>();
                for (LinkedHashSet<String> ts : typeSets) {
                    for (String t : ts) {
                        types.add(t);
                    }
                }
            }
            ArrayList<Double> scores = getScores(types, quantityDesc);
            double lScore = 0;
            int index = 0;
            for (LinkedHashSet<String> ts : typeSets) {
                double currentMax = 0;
                for (String t : ts) {
                    currentMax = Math.max(currentMax, scores.get(index++) * type2Itf.get(t));
                }
                lScore += currentMax;
            }
            return lScore / typeSets.size();
        }
    }

    private class BacktrackJointInferenceInfo {
        int[] entityColumnIndexes;
        int[] numericColumnIndexes;

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
            for (int i : numericColumnIndexes) {
                bestColumnLinking[i] = currentColumnLinking[i];
                bestColumnLinkingScore[i] = currentColumnLinkingScore[i];
            }
            for (int i = 0; i < bestEntityAssignment.length; ++i) {
                for (int j : entityColumnIndexes) {
                    bestEntityAssignment[i][j] = currentEntityAssignment[i][j];
                }
            }
            bestJointScore = currentJointScore;
        }


        private ColumnType[] buildColumnTypeSetForCurrentAssignment() {
            ColumnType[] columnTypeSet = new ColumnType[table.nColumn];
            for (int i : entityColumnIndexes) {
                columnTypeSet[i] = buildColumnTypeForCurrentAssignment(i);
            }
            return columnTypeSet;
        }

        private ColumnType buildColumnTypeForCurrentAssignment(int eCol) {
            // double check if eCol is entity column
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
                LinkedHashSet<String> typeSet = new LinkedHashSet<>();
                for (Pair<String, Double> p : types) {
                    ct.type2Itf.putIfAbsent(p.first, p.second);
                    typeSet.add(p.first);
                }
                ct.typeSets.add(typeSet);
            }
            return ct;
        }


        public void recomputeBasedOnCurrentAssignment() {
            // Compute currentColumnLinkingScore, currentJointScore
            ColumnType[] columnTypeSet = buildColumnTypeSetForCurrentAssignment();

            double homogeneity = 0;
            double connectivity = 0;

            int firstColumn = table.getFirstNonNumericColumn();
            for (int i : entityColumnIndexes) {
                // homogeneity
                homogeneity += (currentHomogeneityScore[i] = columnTypeSet[i].getHScore());
            }
            homogeneity /= entityColumnIndexes.length;

            if (homogeneityWeight != Constants.MAX_DOUBLE) { // only compute if weight for connectivity != 0
                for (int i : numericColumnIndexes) {
                    // connectivity
                    ColumnType ct = columnTypeSet[currentColumnLinking[i]];
                    // (1) combined quantity header
                    double lScore = ct.getLScore(table.getQuantityDescriptionFromCombinedHeader(i));
                    // (2) last quantity header
                    if (table.nHeaderRow > 1) {
                        lScore = Math.max(lScore, ct.getLScore(table.getQuantityDescriptionFromLastHeader(i)));
                    }
                    lScore = (1 - FIRST_COLUMN_WEIGHT) * lScore + FIRST_COLUMN_WEIGHT * (currentColumnLinking[i] == firstColumn ? 1 : 0);
                    connectivity += (currentColumnLinkingScore[i] = lScore);
                }
                connectivity /= numericColumnIndexes.length;
            }

            // joint score
            currentJointScore = homogeneityWeight != Constants.MAX_DOUBLE ? connectivity + homogeneityWeight * homogeneity : homogeneity;
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

            int firstColumn = table.getFirstNonNumericColumn();
            for (int i : entityColumnIndexes) {
                // homogeneity
                if (i != col) {
                    homogeneity += currentHomogeneityScore[i];
                } else {
                    columnTypeSet[i] = buildColumnTypeForCurrentAssignment(i);
                    homogeneity += columnTypeSet[i].getHScore();
                }
            }
            homogeneity /= entityColumnIndexes.length;

            if (homogeneityWeight != Constants.MAX_DOUBLE) { // only compute if weight for connectivity != 0
                for (int i : numericColumnIndexes) {
                    // connectivity
                    if (currentColumnLinking[i] != col) {
                        connectivity += currentColumnLinkingScore[i];
                    } else {
                        ColumnType ct = columnTypeSet[currentColumnLinking[i]];
                        // (1) combined quantity header
                        double lScore = ct.getLScore(table.getQuantityDescriptionFromCombinedHeader(i));
                        // (2) last quantity header
                        if (table.nHeaderRow > 1) {
                            lScore = Math.max(lScore, ct.getLScore(table.getQuantityDescriptionFromLastHeader(i)));
                        }
                        lScore = (1 - FIRST_COLUMN_WEIGHT) * lScore + FIRST_COLUMN_WEIGHT * (currentColumnLinking[i] == firstColumn ? 1 : 0);
                        connectivity += lScore;
                    }
                }
                connectivity /= numericColumnIndexes.length;
            }

            // restore old candidate
            currentEntityAssignment[row][col] = oldCandidate;

            // joint score
            return homogeneityWeight != Constants.MAX_DOUBLE ? connectivity + homogeneityWeight * homogeneity : homogeneity;
        }
    }

    private void backtrackJointInference(Table table, BacktrackJointInferenceInfo info, int currentCol) {
        // backtracking all possible column linking
        if (currentCol < info.numericColumnIndexes.length) {
            for (int i : info.entityColumnIndexes) {
                info.currentColumnLinking[info.numericColumnIndexes[currentCol]] = i;
                backtrackJointInference(table, info, currentCol + 1);
                if (homogeneityWeight == Constants.MAX_DOUBLE) {
                    // if connectivity weight = 0, then only check the first column alignment
                    break;
                }
            }
            return;
        }

        // Now process the ICA algorithm;
        // Init candidates
        for (int j : info.entityColumnIndexes) {
            for (int i = 0; i < table.nDataRow; ++i) {
                EntityLink el = table.data[i][j].getRepresentativeEntityLink();
                if (el != null) {
                    info.currentEntityAssignment[i][j] = info.savedEntityAssignment[i][j] = el.candidates.get(0).first;
                }
            }
        }
        info.recomputeBasedOnCurrentAssignment();

        // Iterative classifying
        if (inferenceMode == JOINT_INFERENCE) {
            int nIterations = 0;
            boolean hasChange;
            do {
                hasChange = false;
                for (int i = 0; i < table.nDataRow; ++i) {
                    for (int j : info.entityColumnIndexes) {
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
                        for (int j : info.entityColumnIndexes) {
                            info.currentEntityAssignment[i][j] = info.savedEntityAssignment[i][j];
                        }
                    }
                    info.recomputeBasedOnCurrentAssignment();
                }
            } while (++nIterations < JOINT_MAX_NUM_ITERS && hasChange);
        }
        // Compare with best assignment
        if (info.currentJointScore > info.bestJointScore) {
            info.captureCurrentColumnLinking();
        }
    }

    private boolean jointInference(Table table) {
        // prune too large tables
        int nECols = 0, nQCols = 0;
        for (int i = 0; i < table.nColumn; ++i) {
            if (table.isEntityColumn[i]) {
                ++nECols;
            } else if (table.isNumericColumn[i]) {
                ++nQCols;
            }
        }
        if (JOINT_MAX_NUM_COLUMN_LINKING >= 0 && Math.pow(nECols, nQCols) > JOINT_MAX_NUM_COLUMN_LINKING) {
            LOGGER.info("Prune large table: " + table._id);
            return false;
        }

        BacktrackJointInferenceInfo info = new BacktrackJointInferenceInfo(table);
        // fill entity & quantity column indexes
        info.entityColumnIndexes = new int[nECols];
        info.numericColumnIndexes = new int[nQCols];
        nECols = 0;
        nQCols = 0;
        for (int i = 0; i < table.nColumn; ++i) {
            if (table.isEntityColumn[i]) {
                info.entityColumnIndexes[nECols++] = i;
            } else if (table.isNumericColumn[i]) {
                info.numericColumnIndexes[nQCols++] = i;
            }
        }

        // backtracking
        backtrackJointInference(table, info, 0);

        // set candidates back to tables
        for (int i = 0; i < table.nDataRow; ++i) {
            for (int j = 0; j < table.nColumn; ++j) {
                // remove all candidates of entity links to reduce size
                // (only in the body - because the header is not tagged).
                // this also include entity cells of non-entity columns (which uses prior-based result)
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
        for (int i : info.numericColumnIndexes) {
            table.quantityToEntityColumn[i] = info.bestColumnLinking[i];
            table.quantityToEntityColumnScore[i] = info.bestColumnLinkingScore[i];
        }

        return true;
    }
}
