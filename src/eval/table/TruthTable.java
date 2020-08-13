package eval.table;


import model.table.Table;
import model.table.link.EntityLink;
import util.Gson;
import util.Pair;

import java.util.Arrays;
import java.util.HashSet;

// For ground truth of column linkings.
public class TruthTable extends Table {
    public int[] quantityToEntityColumnGroundTruth; // -1 means there is no connection.

    public String[][] bodyEntityTarget; // e.g. <Cristiano_Ronaldo> (with < and > )

    // temporary for evaluation
    public int[][] yusraBodyEntityTarget;

    public static TruthTable fromTable(Table t) {
        TruthTable truth = Gson.fromJson(Gson.toJson(t), TruthTable.class);

        truth.quantityToEntityColumnGroundTruth = new int[truth.nColumn];
        Arrays.fill(truth.quantityToEntityColumnGroundTruth, -1);

        truth.bodyEntityTarget = new String[truth.nDataRow][truth.nColumn];
        truth.yusraBodyEntityTarget = new int[truth.nDataRow][truth.nColumn];

        return truth;
    }

    // return -1 means there is no mention.
    public double getEntityDisambiguationPrecisionFromPrior() {
        Pair<Integer, Integer> result = getEntityDisambiguationMicroPrecisionInfoFromPrior();
        return result == null ? -1 : ((double) result.first) / result.second;
    }

    // return null means there is no mention.
    public Pair<Integer, Integer> getEntityDisambiguationMicroPrecisionInfoFromPrior() {
        int total = 0;
        int nTrue = 0;
        for (int i = 0; i < bodyEntityTarget.length; ++i) {
            for (int j = 0; j < bodyEntityTarget[i].length; ++j) {
                EntityLink el = data[i][j].getRepresentativeEntityLink();
                if (el == null) {
                    continue;
                }
                ++total;

                String target = el.candidates.get(0).first;
                if (bodyEntityTarget[i][j].equals(target)) {
                    ++nTrue;
                }

//                el.target = "YAGO:" + target.substring(1, target.length() - 1);
            }
        }
        if (total == 0) {
            return null;
        }
        return new Pair<>(nTrue, total);
    }

    // return -1 means there is no mention.
    public double getEntityDisambiguationPrecisionFromTarget() {
        Pair<Integer, Integer> result = getEntityDisambiguationMicroPrecisionInfoFromTarget();
        return result == null ? -1 : ((double) result.first) / result.second;
    }

    // return null means there is no mention.
    public Pair<Integer, Integer> getEntityDisambiguationMicroPrecisionInfoFromTarget() {
        int total = 0;
        int nTrue = 0;
        for (int i = 0; i < bodyEntityTarget.length; ++i) {
            for (int j = 0; j < bodyEntityTarget[i].length; ++j) {
                EntityLink el = data[i][j].getRepresentativeEntityLink();
                if (el == null) {
                    continue;
                }
                ++total;

                String predictedTarget = data[i][j].getRepresentativeEntityLink().target;
                predictedTarget = "<" + predictedTarget.substring(predictedTarget.lastIndexOf(":") + 1) + ">";
                if (predictedTarget.equals(bodyEntityTarget[i][j])) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return null;
        }
        return new Pair<>(nTrue, total);
    }

    // return -1 means there is no alignment.
    public double getAlignmentPrecisionFromTarget() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;
                if (quantityToEntityColumnGroundTruth[i] == quantityToEntityColumn[i]) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    // return -1 means there is no alignment.
    public double getAlignmentPrecisionFromFirstColumn() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        int eColumn = (hasIndexColumn || isNumericColumn[0]) ? 1 : 0;
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;
                if (quantityToEntityColumnGroundTruth[i] == eColumn) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    // return -1 means there is no alignment.
    public double getAlignmentPrecisionFromFirstEntityColumn() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        int eColumn = -1;
        for (int i = 0; i < nColumn; ++i) {
            if (isEntityColumn[i]) {
                eColumn = i;
                break;
            }
        }
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;
                if (quantityToEntityColumnGroundTruth[i] == eColumn) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    // return -1 means there is no alignment.
    public double getAlignmentPrecisionFromMostUniqueEntityColumnFromTheLeft() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        int eColumn = -1, nUnique = 0;
        for (int i = 0; i < nColumn; ++i) {
            if (isEntityColumn[i]) {
                HashSet<String> set = new HashSet<>();
                for (int r = 0; r < nDataRow; ++r) {
                    set.add(data[r][i].text);
                }
                if (set.size() > nUnique) {
                    nUnique = set.size();
                    eColumn = i;
                }
            }
        }
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;
                if (quantityToEntityColumnGroundTruth[i] == eColumn) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    // return -1 means there is no alignment.
    public double getAlignmentPrecisionFromClosetEntityColumnToTheLeft() {
        int total = 0;
        int nTrue = 0;
        for (int i = 0; i < nColumn; ++i) {

            // ignore evaluating index column.
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;
                for (int j = i - 1; j >= 0; --j) {
                    if (isEntityColumn[j]) {
                        if (j == quantityToEntityColumnGroundTruth[i]) {
                            ++nTrue;
                        }
                        break;
                    }
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    // return -1 means there is no alignment.
    public double getAlignmentPrecisionFromMostUniqueColumnFromTheLeft() {
        int total = 0;
        int nTrue = 0;
        boolean hasIndexColumn = hasIndexColumn();
        for (int i = 0; i < nColumn; ++i) {
            // ignore evaluating index column.
            if (hasIndexColumn && i == 0) {
                continue;
            }
            int eCol = -1, nUnique = 0;
            for (int k = 0; k < nColumn; ++k) {
                if ((k == 0 && (hasIndexColumn || isNumericColumn[0])) || k == i) {
                    continue;
                }
                HashSet<String> set = new HashSet<>();
                for (int r = 0; r < nDataRow; ++r) {
                    set.add(data[r][k].text);
                }
                if (set.size() > nUnique) {
                    nUnique = set.size();
                    eCol = k;
                }
            }
            if (quantityToEntityColumnGroundTruth[i] != -1) {
                ++total;
                if (quantityToEntityColumnGroundTruth[i] == eCol) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    // return -1 means there is no mention.
    public double getEntityDisambiguationPrecisionFromYusra() {
        Pair<Integer, Integer> result = getEntityDisambiguationMicroPrecisionInfoFromYusra();
        return result == null ? -1 : ((double) result.first) / result.second;
    }

    // return null means there is no mention.
    public Pair<Integer, Integer> getEntityDisambiguationMicroPrecisionInfoFromYusra() {
        int total = 0;
        int nTrue = 0;
        for (int i = 0; i < bodyEntityTarget.length; ++i) {
            for (int j = 0; j < bodyEntityTarget[i].length; ++j) {
                EntityLink el = data[i][j].getRepresentativeEntityLink();
                if (el == null) {
                    continue;
                }
                ++total;

                if (yusraBodyEntityTarget[i][j] == 1) {
                    ++nTrue;
                }
            }
        }
        if (total == 0) {
            return null;
        }
        return new Pair<>(nTrue, total);
    }
}
