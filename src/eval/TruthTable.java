package eval;


import com.google.gson.Gson;
import model.table.Table;
import model.table.link.EntityLink;

import java.util.Arrays;

// For ground truth of column linkings.
public class TruthTable extends Table {
    private static final transient Gson GSON = new Gson();

    @Deprecated
    public int keyColumnGroundTruth = -1;

    public int[] quantityToEntityColumnGroundTruth; // -1 means there is no connection.

    public String[][] bodyEntityTarget; // e.g. <Cristiano_Ronaldo> (with < and > )

    // temporary for evaluation
    public int[][] yusraBodyEntityTarget;

    public static TruthTable fromTable(Table t) {
        TruthTable truth = GSON.fromJson(GSON.toJson(t), TruthTable.class);

        truth.quantityToEntityColumnGroundTruth = new int[truth.nColumn];
        Arrays.fill(truth.quantityToEntityColumnGroundTruth, -1);

        truth.bodyEntityTarget = new String[truth.nDataRow][truth.nColumn];
        truth.yusraBodyEntityTarget = new int[truth.nDataRow][truth.nColumn];

        return truth;
    }

    // return -1 means there is no mention.
    public double getEntityDisambiguationPrecisionFromFirstCandidate() {
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
            }
        }
        if (total == 0) {
            return -1;
        }
        return ((double) nTrue) / total;
    }

    // return -1 means there is no mention.
    public double getEntityDisambiguationPrecisionFromTarget() {
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
            return -1;
        }
        return ((double) nTrue) / total;
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

    // return -1 means there is no mention.
    public double getEntityDisambiguationPrecisionFromYusra() {
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
            return -1;
        }
        return ((double) nTrue) / total;
    }
}
