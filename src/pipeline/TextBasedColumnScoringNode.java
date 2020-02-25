package pipeline;

import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import util.Pair;
import util.Triple;
import yago.QfactTaxonomyGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

// Link quantity columns to entity columns, return false if there is no quantity column.
// This class uses TaxonomyGraph class, using non-transitive type system.
public class TextBasedColumnScoringNode implements TaggingNode {
    public static final Logger LOGGER = Logger.getLogger(TextBasedColumnScoringNode.class.getName());

    public static final int PRIOR_INFERENCE = 1;
    public static final int JOINT_INFERENCE = 2;
    public static final int INDEPENDENT_INFERENCE = 3;

    // TODO: fix this weight
    public static final double DEFAULT_JOINT_HOMOGENEITY_WEIGHT = 0.7;

    public static final int JOINT_MAX_NUM_ITERS = 100;
    public static final int JOINT_MAX_LOCAL_CANDIDATES = 10; // set to -1 to disable this threshold. (-1 means INF)
    public static int JOINT_MAX_NUM_COLUMN_LINKING = 100; // to prune too large tables. (-1 means INF)

    private int inferenceMode;
    private double homogeneityWeight;
    private QfactTaxonomyGraph qfactGraph;

    private TextBasedColumnScoringNode(int inferenceMode, double homogeneityWeight) {
        this.inferenceMode = inferenceMode;
        this.qfactGraph = new QfactTaxonomyGraph();
        this.homogeneityWeight = homogeneityWeight;
    }

    public static TextBasedColumnScoringNode getIndependentInferenceInstance() {
        return new TextBasedColumnScoringNode(INDEPENDENT_INFERENCE, 1);
    }

    public static TextBasedColumnScoringNode getJointInferenceInstance(double homogeneityWeight) {
        return new TextBasedColumnScoringNode(JOINT_INFERENCE, homogeneityWeight);
    }

    public static TextBasedColumnScoringNode getJointInferenceInstance() {
        return getJointInferenceInstance(DEFAULT_JOINT_HOMOGENEITY_WEIGHT);
    }

    public static TextBasedColumnScoringNode getPriorInferenceInstance() {
        return new TextBasedColumnScoringNode(PRIOR_INFERENCE, 1);
    }

    @Override
    public boolean process(Table table) {
        qfactGraph.resetCache();
        table.quantityToEntityColumn = new int[table.nColumn];
        Arrays.fill(table.quantityToEntityColumn, -1);

        table.quantityToEntityColumnScore = new double[table.nColumn];
        Arrays.fill(table.quantityToEntityColumnScore, -1.0);

        if (inferenceMode == PRIOR_INFERENCE || inferenceMode == JOINT_INFERENCE || inferenceMode == INDEPENDENT_INFERENCE) {
            return inference(table);
        } else {
            throw new RuntimeException("Invalid inference mode");
        }
    }

    private class ColumnHomogeneityInfo {
        public ArrayList<Integer> entityIds = new ArrayList<>();
        public ArrayList<Double> entityPrior = new ArrayList<>();

        // TODO: fix this weight
        public static final double PRIOR_WEIGHT = 0.9;

        public double getHScore() {
            double agreeScore = 0;
            if (entityIds.size() > 1) {
                for (int i = 0; i < entityIds.size(); ++i) {
                    for (int j = i + 1; j < entityIds.size(); ++j) {
                        agreeScore += qfactGraph.getTypeAgreement(entityIds.get(i), entityIds.get(j));
                    }
                }
                agreeScore /= (entityIds.size() * (entityIds.size() - 1) / 2);
            }

            double priorScore = 0;
            for (int i = 0; i < entityPrior.size(); ++i) {
                priorScore += entityPrior.get(i);
            }
            priorScore /= entityPrior.size();

            return priorScore * PRIOR_WEIGHT + agreeScore * (1 - PRIOR_WEIGHT);
        }
    }

    private class BacktrackJointInferenceInfo {
        public class JointScore {
            double first;
            double second; // only use in case we do ED & CA independently.

            public JointScore() {
                first = second = 0;
            }

            public JointScore(double first, double second) {
                this.first = first;
                this.second = second;
            }

            public int compareTo(JointScore other) {
                if (first != other.first) {
                    return first < other.first ? -1 : 1;
                } else {
                    return second == other.second ? 0 : (second < other.second ? -1 : 1);
                }
            }
        }

        int[] entityColumnIndexes;
        int[] numericColumnIndexes;

        int[] currentColumnLinking;
        Double[][] currentQfactMatchingScore;
        String[][] currentQfactMatchingStr;

        double[] currentColumnLinkingScore;
        Triple<String, Integer, Double>[][] currentEntityAssignment;
        double[] currentHomogeneityScore;
        JointScore currentJointScore;
        Triple<String, Integer, Double>[][] savedEntityAssignment; // For saving each best local assignment

        int[] bestColumnLinking;
        double[] bestColumnLinkingScore;
        String[][] bestEntityAssignment;
        String[][] bestQfactMatchingStr;
        JointScore bestJointScore;

        Table table;

        // null means there is no entity | quantity disambiguated
        // 0 means cannot connect to text.
        public void computeCurrentQfactMatchingScores() {
            for (int qCol : numericColumnIndexes) {
                int eCol = currentColumnLinking[qCol];
                String combinedContext = table.getQuantityDescriptionFromCombinedHeader(qCol, false);
                String lastHeaderContext = table.getQuantityDescriptionFromLastHeader(qCol, false);
                for (int r = 0; r < table.nDataRow; ++r) {
                    currentQfactMatchingScore[r][qCol] = null;
                    currentQfactMatchingStr[r][qCol] = null;
                    Triple<String, Integer, Double> e = currentEntityAssignment[r][eCol];
                    if (e == null) {
                        continue;
                    }
                    QuantityLink ql = table.data[r][qCol].getRepresentativeQuantityLink();
                    if (ql == null) {
                        continue;
                    }

                    // (1) combined quantity header
                    Pair<Double, String> matchResult = qfactGraph.getMatchScore(e.first, combinedContext, ql.quantity, (r * table.nColumn + qCol) * 2);
                    if (matchResult != null) {
                        // we need score, instead of distance
                        if (table.nHeaderRow > 1) {
                            // (2) last quantity header
                            Pair<Double, String> lastHeaderResult = qfactGraph.getMatchScore(e.first, lastHeaderContext, ql.quantity, (r * table.nColumn + qCol) * 2 + 1);
                            if (lastHeaderResult.first > matchResult.first) {
                                matchResult = lastHeaderResult;
                            }
                        }
                    } else {
                        matchResult = new Pair<>(0.0, null);
                    }
                    currentQfactMatchingScore[r][qCol] = matchResult.first;
                    currentQfactMatchingStr[r][qCol] = matchResult.second;
                }
            }
        }

        public BacktrackJointInferenceInfo(Table t) {
            table = t;
            currentColumnLinking = new int[table.nColumn];
            currentQfactMatchingScore = new Double[table.nDataRow][table.nColumn];
            currentQfactMatchingStr = new String[table.nDataRow][table.nColumn];

            currentColumnLinkingScore = new double[table.nColumn];
            savedEntityAssignment = new Triple[table.nDataRow][table.nColumn];
            currentEntityAssignment = new Triple[table.nDataRow][table.nColumn];
            currentHomogeneityScore = new double[table.nColumn];
            currentJointScore = new JointScore();

            // for saving
            bestColumnLinking = new int[table.nColumn];
            bestColumnLinkingScore = new double[table.nColumn];
            bestEntityAssignment = new String[table.nDataRow][table.nColumn];
            bestJointScore = new JointScore(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
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
                    bestEntityAssignment[i][j] = currentEntityAssignment[i][j] != null ? currentEntityAssignment[i][j].first : null;
                }
            }
            bestJointScore = currentJointScore;

            // capture MatchingStr
            bestQfactMatchingStr = new String[table.nDataRow][table.nColumn];
            for (int i = 0; i < table.nDataRow; ++i) {
                for (int j = 0; j < table.nColumn; ++j) {
                    bestQfactMatchingStr[i][j] = currentQfactMatchingStr[i][j];
                }
            }
        }


        private ColumnHomogeneityInfo[] buildColumnHomogeneityInfoSetForCurrentAssignment() {
            ColumnHomogeneityInfo[] chiSet = new ColumnHomogeneityInfo[table.nColumn];
            for (int i : entityColumnIndexes) {
                chiSet[i] = buildColumnHomogeneityInfoSetForCurrentAssignment(i);
            }
            return chiSet;
        }

        private ColumnHomogeneityInfo buildColumnHomogeneityInfoSetForCurrentAssignment(int eCol) {
            // double check if eCol is entity column
            if (!table.isEntityColumn[eCol]) {
                return null;
            }

            ColumnHomogeneityInfo chi = new ColumnHomogeneityInfo();
            for (int i = 0; i < table.nDataRow; ++i) {
                Triple<String, Integer, Double> e = currentEntityAssignment[i][eCol];
                if (e == null) {
                    continue;
                }
                Integer eId = qfactGraph.entity2Id.get(e.first);
                // Here we ignore checking eId != null, because it should be in the KB.

                chi.entityIds.add(eId);
                chi.entityPrior.add(e.third);
            }
            return chi;
        }


        public void recomputeBasedOnCurrentAssignment() {
            // Compute currentColumnLinkingScore, currentJointScore
            ColumnHomogeneityInfo[] chiSet = buildColumnHomogeneityInfoSetForCurrentAssignment();

            double homogeneity = 0;
            double connectivity = 0;

//            int firstColumn = table.getFirstNonNumericColumn();
            for (int i : entityColumnIndexes) {
                // homogeneity
                homogeneity += (currentHomogeneityScore[i] = chiSet[i].getHScore());
            }
            homogeneity /= entityColumnIndexes.length;

            if (homogeneityWeight < 1) { // compute on JOINT only (INDEPENDENT_INFERENCE has homogeneityWeight = 1)
                computeCurrentQfactMatchingScores();
                for (int i : numericColumnIndexes) {
                    double lScore = 0;
                    double nConnect = 0;
                    for (int r = 0; r < table.nDataRow; ++r) {
                        if (currentQfactMatchingScore[r][i] != null) {
                            lScore += currentQfactMatchingScore[r][i];
                            ++nConnect;
                        }
                    }
                    lScore = nConnect > 0 ? lScore / nConnect : 0;

                    connectivity += (currentColumnLinkingScore[i] = lScore);
                }
                connectivity /= numericColumnIndexes.length;
            }

            // joint score
            currentJointScore = new JointScore(homogeneityWeight * homogeneity + (1 - homogeneityWeight) * connectivity, 0);
        }

        public JointScore newScoreOfLocalAssignment(int row, int col, Triple<String, Integer, Double> candidate) {
            // try new candidate
            Triple<String, Integer, Double> oldCandidate = currentEntityAssignment[row][col];
            currentEntityAssignment[row][col] = candidate;
            // now compute

            // partial built column type set
            ColumnHomogeneityInfo[] chiSet = new ColumnHomogeneityInfo[table.nColumn];

            double homogeneity = 0;
            double connectivity = 0;

//            int firstColumn = table.getFirstNonNumericColumn();
            for (int i : entityColumnIndexes) {
                // homogeneity
                if (i != col) {
                    homogeneity += currentHomogeneityScore[i];
                } else {
                    chiSet[i] = buildColumnHomogeneityInfoSetForCurrentAssignment(i);
                    homogeneity += chiSet[i].getHScore();
                }
            }
            homogeneity /= entityColumnIndexes.length;

            if (homogeneityWeight < 1) { // compute on JOINT only (INDEPENDENT_INFERENCE has homogeneityWeight = 1)
                for (int i : numericColumnIndexes) {
                    // connectivity
                    if (currentColumnLinking[i] != col) {
                        connectivity += currentColumnLinkingScore[i];
                    } else {
                        double lScore = 0;
                        double nConnect = 0;
                        for (int r = 0; r < table.nDataRow; ++r) {
                            if (r != row) {
                                if (currentQfactMatchingScore[r][i] != null) {
                                    lScore += currentQfactMatchingScore[r][i];
                                    ++nConnect;
                                }
                            } else {
                                // here is the code for new local value.
                                QuantityLink ql = table.data[r][i].getRepresentativeQuantityLink();
                                if (ql == null) {
                                    continue;
                                }
                                // (1) combined quantity header
                                double matchScr;
                                Pair<Double, String> matchResult = qfactGraph.getMatchScore(candidate.first, table.getQuantityDescriptionFromCombinedHeader(i, false), ql.quantity, (r * table.nColumn + i) * 2);
                                if (matchResult != null) {
                                    // we need score, instead of distance
                                    matchScr = matchResult.first;
                                    if (table.nHeaderRow > 1) {
                                        // (2) last quantity header
                                        matchScr = Math.max(matchScr, qfactGraph.getMatchScore(candidate.first, table.getQuantityDescriptionFromLastHeader(i, false), ql.quantity, (r * table.nColumn + i) * 2 + 1).first);
                                    }
                                } else {
                                    matchScr = 0;
                                }
                                lScore += matchScr;
                                ++nConnect;
                            }
                        }
                        lScore = nConnect > 0 ? lScore / nConnect : 0;

                        connectivity += lScore;
                    }
                }
                connectivity /= numericColumnIndexes.length;
            }

            // restore old candidate
            currentEntityAssignment[row][col] = oldCandidate;

            // joint score
            return new JointScore(homogeneityWeight * homogeneity + (1 - homogeneityWeight) * connectivity, 0);
        }
    }

    private void backtrackJointInference(Table table, BacktrackJointInferenceInfo info, int currentCol) {
        // backtracking all possible column linking
        if (currentCol < info.numericColumnIndexes.length) {
            for (int i : info.entityColumnIndexes) {
                info.currentColumnLinking[info.numericColumnIndexes[currentCol]] = i;
                backtrackJointInference(table, info, currentCol + 1);
                if (homogeneityWeight == 1) {
                    // if connectivity weight = 0, then only check the first column alignment
                    // This is also the case of INDEPENDENT_INFERENCE & PRIOR_INFERENCE
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
                    info.currentEntityAssignment[i][j] = info.savedEntityAssignment[i][j] = el.candidates.get(0);
                }
            }
        }
        info.recomputeBasedOnCurrentAssignment();

        // Iterative classifying
        if (inferenceMode == JOINT_INFERENCE || inferenceMode == INDEPENDENT_INFERENCE) {
            int nIterations = 0;
            boolean hasChange;
            do {
                hasChange = false;
                for (int i = 0; i < table.nDataRow; ++i) {
                    for (int j : info.entityColumnIndexes) {
                        if (info.currentEntityAssignment[i][j] == null) {
                            continue;
                        }
                        BacktrackJointInferenceInfo.JointScore currentLocalScore = info.currentJointScore;
                        int nTried = 0;
                        for (Triple<String, Integer, Double> c : table.data[i][j].getRepresentativeEntityLink().candidates) {
                            if (JOINT_MAX_LOCAL_CANDIDATES >= 0 && ++nTried > JOINT_MAX_LOCAL_CANDIDATES) {
                                break;
                            }
                            if (c.first.equals(info.currentEntityAssignment[i][j])) {
                                continue;
                            }
                            BacktrackJointInferenceInfo.JointScore newLocalScore = info.newScoreOfLocalAssignment(i, j, c);
                            if (newLocalScore.compareTo(currentLocalScore) > 0) {
                                currentLocalScore = newLocalScore;
                                info.savedEntityAssignment[i][j] = c;
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
        if (info.currentJointScore.compareTo(info.bestJointScore) > 0) {
            info.captureCurrentColumnLinking();
        }
    }

    private void independentColumnAlignmentInference(Table table, BacktrackJointInferenceInfo info) {
        // capture MatchingStr
        info.bestQfactMatchingStr = new String[table.nDataRow][table.nColumn];
        // for each qCol
        for (int qCol : info.numericColumnIndexes) {
            // find best eCol for qCol
            int bestECol = -1;
            double bestEColScore = 0;
            for (int eCol : info.entityColumnIndexes) {
                String combinedContext = table.getQuantityDescriptionFromCombinedHeader(qCol, false);
                String lastHeaderContext = table.getQuantityDescriptionFromLastHeader(qCol, false);
                for (int r = 0; r < table.nDataRow; ++r) {
                    info.currentQfactMatchingScore[r][qCol] = null;
                    info.currentQfactMatchingStr[r][qCol] = null;
                    String e = info.bestEntityAssignment[r][eCol];
                    if (e == null) {
                        continue;
                    }
                    QuantityLink ql = table.data[r][qCol].getRepresentativeQuantityLink();
                    if (ql == null) {
                        continue;
                    }

                    // (1) combined quantity header
                    Pair<Double, String> matchResult = qfactGraph.getMatchScore(e, combinedContext, ql.quantity, (r * table.nColumn + qCol) * 2);
                    if (matchResult != null) {
                        // we need score, instead of distance
                        if (table.nHeaderRow > 1) {
                            // (2) last quantity header
                            Pair<Double, String> lastHeaderResult = qfactGraph.getMatchScore(e, lastHeaderContext, ql.quantity, (r * table.nColumn + qCol) * 2 + 1);
                            if (lastHeaderResult.first > matchResult.first) {
                                matchResult = lastHeaderResult;
                            }
                        }
                    } else {
                        matchResult = new Pair<>(0.0, null);
                    }
                    info.currentQfactMatchingScore[r][qCol] = matchResult.first;
                    info.currentQfactMatchingStr[r][qCol] = matchResult.second;
                }

                double lScore = 0;
                double nConnect = 0;
                for (int r = 0; r < table.nDataRow; ++r) {
                    if (info.currentQfactMatchingScore[r][qCol] != null) {
                        lScore += info.currentQfactMatchingScore[r][qCol];
                        ++nConnect;
                    }
                }
                lScore = nConnect > 0 ? lScore / nConnect : 0;

                if (bestECol == -1 || lScore > bestEColScore) {
                    bestECol = eCol;
                    bestEColScore = lScore;
                    for (int r = 0; r < table.nDataRow; ++r) {
                        info.bestQfactMatchingStr[r][qCol] = info.currentQfactMatchingStr[r][qCol];
                    }
                }
            }
            info.bestColumnLinking[qCol] = bestECol;
            info.bestColumnLinkingScore[qCol] = bestEColScore;
            info.bestJointScore.second += bestEColScore / info.numericColumnIndexes.length;
        }
    }

    private boolean inference(Table table) {
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
        // for INDEPENDENT_INFERENCE & PRIOR_INFERENCE, this call only does entity disambiguation
        backtrackJointInference(table, info, 0);

        // for INDEPENDENT_INFERENCE & PRIOR_INFERENCE, column alignment needs to be computed separately here
        if (inferenceMode == INDEPENDENT_INFERENCE || inferenceMode == PRIOR_INFERENCE) {
            independentColumnAlignmentInference(table, info);
        }

        table.QfactMatchingStr = new String[table.nDataRow][table.nColumn];
        // set candidates back to tables
        for (int i = 0; i < table.nDataRow; ++i) {
            for (int j = 0; j < table.nColumn; ++j) {
                table.QfactMatchingStr[i][j] = info.bestQfactMatchingStr[i][j];
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
