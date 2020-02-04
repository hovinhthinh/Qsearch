package pipeline;

import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import nlp.YagoType;
import util.Constants;
import util.Pair;
import yago.QfactTaxonomyGraph;

import java.util.*;
import java.util.logging.Logger;

// Link quantity columns to entity columns, return false if there is no quantity column.
// TODO
@Deprecated
public class TextBasedColumnScoringNode implements TaggingNode {
    public static final Logger LOGGER = Logger.getLogger(TextBasedColumnScoringNode.class.getName());

    public static final int PRIOR_INFERENCE = 1;
    public static final int JOINT_INFERENCE = 2;

    // TODO: fix this weight
    public static final double DEFAULT_JOINT_HOMOGENEITY_WEIGHT = 0.7;

    public static final int JOINT_MAX_NUM_ITERS = 100;
    public static final int JOINT_MAX_LOCAL_CANDIDATES = 10; // set to -1 to disable this threshold. (-1 means INF)
    public static final int JOINT_MAX_NUM_COLUMN_LINKING = 100; // to prune too large tables. (-1 means INF)

    private int inferenceMode;
    private double homogeneityWeight;
    private QfactTaxonomyGraph qfactGraph;

    public TextBasedColumnScoringNode(int inferenceMode, double homogeneityWeight) {
        this.inferenceMode = inferenceMode;
        this.qfactGraph = new QfactTaxonomyGraph();
        this.homogeneityWeight = homogeneityWeight;
    }

    public TextBasedColumnScoringNode(int inferenceMode) {
        this(inferenceMode, DEFAULT_JOINT_HOMOGENEITY_WEIGHT);
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
    }

    private class BacktrackJointInferenceInfo {
        int[] entityColumnIndexes;
        int[] numericColumnIndexes;

        int[] currentColumnLinking;
        Double[][] currentQfactMatchingScore;
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

        // null means there is no entity | quantity disambiguated
        // Constants.MIN_DOUBLE means cannot connect to text.
        public void computeCurrentQfactMatchingScores() {
            for (int qCol : numericColumnIndexes) {
                int eCol = currentColumnLinking[qCol];
                String combinedContext = table.getQuantityDescriptionFromCombinedHeader(qCol, false);
                String lastHeaderContext = table.getQuantityDescriptionFromLastHeader(qCol, false);
                for (int r = 0; r < table.nDataRow; ++r) {
                    currentQfactMatchingScore[r][qCol] = null;
                    String e = currentEntityAssignment[r][eCol];
                    if (e == null) {
                        continue;
                    }
                    QuantityLink ql = table.data[r][qCol].getRepresentativeQuantityLink();
                    if (ql == null) {
                        continue;
                    }

                    // (1) combined quantity header
                    double matchScr;
                    Pair<Double, String> matchResult = qfactGraph.getMatchDistance(e, combinedContext, ql.quantity);
                    if (matchResult != null) {
                        // we need score, instead of distance
                        matchScr = -matchResult.first;
                        if (table.nHeaderRow > 1) {
                            // (2) last quantity header
                            matchScr = Math.max(matchScr, -qfactGraph.getMatchDistance(e, lastHeaderContext, ql.quantity).first);
                        }
                    } else {
                        matchScr = Constants.MIN_DOUBLE;
                    }
                    currentQfactMatchingScore[r][qCol] = matchScr;
                }
            }
        }

        public BacktrackJointInferenceInfo(Table t) {
            table = t;
            currentColumnLinking = new int[table.nColumn];
            currentQfactMatchingScore = new Double[table.nDataRow][table.nColumn];
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

            if (homogeneityWeight < 1) { // only compute if weight for connectivity != 0
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
                    lScore = nConnect > 0 ? lScore / nConnect : Constants.MIN_DOUBLE;

                    connectivity += (currentColumnLinkingScore[i] = lScore);
                }
                connectivity /= numericColumnIndexes.length;
            }

            // joint score
            currentJointScore = (1 - homogeneityWeight) * connectivity + homogeneityWeight * homogeneity;
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

            if (homogeneityWeight < 1) { // only compute if weight for connectivity != 0
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
                                Pair<Double, String> matchResult = qfactGraph.getMatchDistance(candidate, table.getQuantityDescriptionFromCombinedHeader(i, false), ql.quantity);
                                if (matchResult != null) {
                                    // we need score, instead of distance
                                    matchScr = -matchResult.first;
                                    if (table.nHeaderRow > 1) {
                                        // (2) last quantity header
                                        matchScr = Math.max(matchScr, -qfactGraph.getMatchDistance(candidate, table.getQuantityDescriptionFromLastHeader(i, false), ql.quantity).first);
                                    }
                                } else {
                                    matchScr = Constants.MIN_DOUBLE;
                                }
                                lScore += matchScr;
                                ++nConnect;
                            }
                        }
                        lScore = nConnect > 0 ? lScore / nConnect : Constants.MIN_DOUBLE;

                        connectivity += lScore;
                    }
                }
                connectivity /= numericColumnIndexes.length;
            }

            // restore old candidate
            currentEntityAssignment[row][col] = oldCandidate;

            // joint score
            return (1 - homogeneityWeight) * connectivity + homogeneityWeight * homogeneity;
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
