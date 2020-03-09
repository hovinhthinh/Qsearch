package pipeline;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import misc.WikipediaEntity;
import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import nlp.NLP;
import uk.ac.susx.informatics.Morpha;
import util.Pair;
import util.Triple;
import yago.QfactTaxonomyGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.logging.Logger;

// Link quantity columns to entity columns, return false if there is no quantity column.
// This class uses TaxonomyGraph class, using non-transitive type system.
public class TextBasedColumnScoringNode implements TaggingNode {
    public static final Logger LOGGER = Logger.getLogger(TextBasedColumnScoringNode.class.getName());

    public static final int PRIOR_INFERENCE = 1;
    public static final int JOINT_INFERENCE = 2;
    public static final int INDEPENDENT_INFERENCE = 3;

    // Homogeneity weights
    // TODO: fix this weight
    public static double PRIOR_WEIGHT = 0.5;
    public static double COOCCUR_WEIGHT = 0;
    public static double CONTEXT_WEIGHT = 0.5;
    public static double AGREE_WEIGHT = -1; // UNUSED this should be derived from above two

    // this normalized value covers at least 99 %
    public static double COOCCUR_NORMALIZED_VALUE = 8;


    // TODO: fix this weight
    public static final double DEFAULT_JOINT_HOMOGENEITY_WEIGHT = 1.0;

    public static final int JOINT_MAX_NUM_ITERS = 100;
    public static final int JOINT_MAX_LOCAL_CANDIDATES = 10; // set to -1 to disable this threshold. (-1 means INF)
    public static int JOINT_MAX_NUM_COLUMN_LINKING = 100; // to prune too large tables. (-1 means INF)

    public int inferenceMode;
    public double homogeneityWeight;
    private QfactTaxonomyGraph qfactGraph;

    private static HashSet<String> BLOCKED_OVERLAP_CONTEXT_TOKENS = new HashSet<>(Arrays.asList(
            "~", "`", "!", "@", "#", "^", "&", "*", "(", ")", "_", "=", "{", "}", "-", "+",
            "[", "]", "\\", "|", ":", ";", "\"", "'", ",", ".", "/", "?", "<", ">"
    ));

    private static final int ENTITY_PAGE_CONTENT_CACHE_SIZE = 1000;
    private Object2ObjectLinkedOpenHashMap<String, HashSet<String>> entityPageContentCache = new Object2ObjectLinkedOpenHashMap<>(ENTITY_PAGE_CONTENT_CACHE_SIZE);


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

    private HashSet<String> getEntityPageContentUniqueTerms(String entity) {
        HashSet<String> result = entityPageContentCache.getAndMoveToFirst(entity);
        if (result != null) {
            return result;
        }

        result = WikipediaEntity.getTermSetOfEntityPage(entity);

        entityPageContentCache.putAndMoveToFirst(entity, result);
        if (entityPageContentCache.size() > ENTITY_PAGE_CONTENT_CACHE_SIZE) {
            entityPageContentCache.removeLast();
        }
        return result;
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
        JointScore currentJointScore;
        Triple<String, Integer, Double>[][] savedEntityAssignment; // For saving each best local assignment

        int[] bestColumnLinking;
        double[] bestColumnLinkingScore;
        String[][] bestEntityAssignment;
        String[][] bestQfactMatchingStr;
        JointScore bestJointScore;

        Table table;

        // below is precomputed information of table
        HashSet<String>[][] cellContext;

        void buildCellContext() {
            HashSet<String> headerContext = new HashSet<>();
            HashSet<String>[] rowContext = new HashSet[table.nDataRow];
            HashSet<String>[] columnContext = new HashSet[table.nColumn];
            for (int i = 0; i < table.nDataRow; ++i) {
                rowContext[i] = new HashSet<>();
            }
            for (int i = 0; i < table.nColumn; ++i) {
                columnContext[i] = new HashSet<>();
            }

            // header context
            for (int i = 0; i < table.nHeaderRow; ++i) {
                for (int j = 0; j < table.nColumn; ++j) {
                    headerContext.addAll(NLP.splitSentence(NLP.fastStemming(table.header[i][j].text.toLowerCase(), Morpha.any)));
                }
            }
            // row & column context
            for (int i = 0; i < table.nDataRow; ++i) {
                for (int j = 0; j < table.nColumn; ++j) {
                    ArrayList<String> terms = NLP.splitSentence(NLP.fastStemming(table.data[i][j].text.toLowerCase(), Morpha.any));
                    rowContext[i].addAll(terms);
                    columnContext[j].addAll(terms);
                }
            }

            // cell context
            cellContext = new HashSet[table.nDataRow][table.nColumn];
            for (int i = 0; i < table.nDataRow; ++i) {
                for (int j = 0; j < table.nColumn; ++j) {
                    cellContext[i][j] = new HashSet<>();
                    cellContext[i][j].addAll(headerContext);
                    cellContext[i][j].addAll(rowContext[i]);
                    cellContext[i][j].addAll(columnContext[j]);
                }
            }
        }

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


        // all terms should be stemmed and lowercased.
        private double getContextScore(HashSet<String> entityTableContext, HashSet<String> entityPageContext) {
            if (entityPageContext == null) {
                return 0;
            }
            int nCommon = 0;
            int nTotal = 0;
            for (String t : entityTableContext) {
                if (NLP.BLOCKED_STOPWORDS.contains(t) || BLOCKED_OVERLAP_CONTEXT_TOKENS.contains(t)) {
                    continue;
                }
                ++nTotal;
                if (entityPageContext.contains(t)) {
                    ++nCommon;
                }
            }
            return nTotal == 0 ? 0 : 1.0 * nCommon / nTotal;
        }

        public double getHomogeneityScoreFromCurrentEntityAssignment() {
            Integer[][] entityIds = new Integer[table.nDataRow][entityColumnIndexes.length];
            double[][] entityPrior = new double[table.nDataRow][entityColumnIndexes.length];

            for (int i = 0; i < entityColumnIndexes.length; ++i) {
                int eCol = entityColumnIndexes[i];
                for (int r = 0; r < table.nDataRow; ++r) {
                    Triple<String, Integer, Double> e = currentEntityAssignment[r][eCol];
                    if (e == null) {
                        continue;
                    }
                    // Here we ignore checking eId != null, because it should be in the KB.
                    entityIds[r][i] = qfactGraph.entity2Id.get(e.first);

                    entityPrior[r][i] = e.third;
                }
            }


            // type agreement
            int nAgreeEdges = 0;
            double agreeScore = 0;

            // entity prior
            int nPriorNodes = 0;
            double priorScore = 0;

            // entity context
            int nContextNodes = 0;
            double contextScore = 0;
            if (cellContext == null) {
                buildCellContext();
            }

            for (int i = 0; i < entityColumnIndexes.length; ++i) {
                for (int r1 = 0; r1 < table.nDataRow; ++r1) {
                    Triple<String, Integer, Double> e = currentEntityAssignment[r1][entityColumnIndexes[i]];
                    if (e == null) {
                        continue;
                    }
                    ++nPriorNodes;
                    priorScore += entityPrior[r1][i];

                    ++nContextNodes;
                    if (CONTEXT_WEIGHT > 0) {
                        contextScore += getContextScore(cellContext[r1][entityColumnIndexes[i]], getEntityPageContentUniqueTerms(e.first));
                    }

                    for (int r2 = r1 + 1; r2 < table.nDataRow; ++r2) {
                        if (entityIds[r2][i] == null) {
                            continue;
                        }
                        agreeScore += qfactGraph.getTypeAgreement(entityIds[r1][i], entityIds[r2][i]);
                        ++nAgreeEdges;
                    }
                }
            }
            if (nAgreeEdges > 0) {
                agreeScore /= nAgreeEdges;
            }
            if (nPriorNodes > 0) {
                priorScore /= nPriorNodes;
            }
            if (nContextNodes > 0) {
                contextScore /= nContextNodes;
            }

            // entity-row coocurrence
            int nEntityCooccurEdges = 0;
            double cooccurScore = 0;

            for (int r = 0; r < table.nDataRow; ++r) {
                for (int i = 0; i < entityColumnIndexes.length; ++i) {
                    Triple<String, Integer, Double> e1 = currentEntityAssignment[r][entityColumnIndexes[i]];
                    if (e1 == null) {
                        continue;
                    }
                    for (int j = i + 1; j < entityColumnIndexes.length; ++j) {
                        Triple<String, Integer, Double> e2 = currentEntityAssignment[r][entityColumnIndexes[j]];
                        if (e2 == null) {
                            continue;
                        }
                        ++nEntityCooccurEdges;
                        if (COOCCUR_WEIGHT > 0) {
                            cooccurScore += WikipediaEntity.getCoocurrencePageCountOfEntities(e1.first, e2.first) / COOCCUR_NORMALIZED_VALUE;
                        }
                    }
                }
            }

            if (nEntityCooccurEdges > 0) {
                cooccurScore /= nEntityCooccurEdges;
            }

            return priorScore * PRIOR_WEIGHT
                    + cooccurScore * COOCCUR_WEIGHT
                    + contextScore * CONTEXT_WEIGHT
                    + agreeScore * (1 - PRIOR_WEIGHT - COOCCUR_WEIGHT - CONTEXT_WEIGHT);
        }

        public void recomputeBasedOnCurrentAssignment() {
            // Compute currentColumnLinkingScore, currentJointScore
            double homogeneity = getHomogeneityScoreFromCurrentEntityAssignment();
            double connectivity = 0;

            if (homogeneityWeight < 1 && inferenceMode == JOINT_INFERENCE) { // compute on JOINT only (INDEPENDENT_INFERENCE has homogeneityWeight = 1)
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
            double homogeneity = getHomogeneityScoreFromCurrentEntityAssignment();
            double connectivity = 0;

            if (homogeneityWeight < 1 && inferenceMode == JOINT_INFERENCE) { // compute on JOINT only (INDEPENDENT_INFERENCE has homogeneityWeight = 1)
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
                if (homogeneityWeight == 1 || inferenceMode == INDEPENDENT_INFERENCE || inferenceMode == PRIOR_INFERENCE) {
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
                            if (lastHeaderResult != null && lastHeaderResult.first > matchResult.first) {
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

        // consolidate column linking
        for (int i : info.numericColumnIndexes) {
            int currentECol = table.quantityToEntityColumn[i];
            String currentEColHeader = table.getOriginalCombinedHeader(currentECol);
            if (currentEColHeader.isEmpty()) {
                continue;
            }
            for (int j = currentECol - 1; j >= 0; --j) {
                if (table.isEntityColumn[j] && table.getOriginalCombinedHeader(j).equals(currentEColHeader)) {
                    table.quantityToEntityColumn[i] = j;
                    break;
                }
            }
        }
        return true;
    }
}
