package data.manual;


import com.google.gson.Gson;
import model.table.Table;

import java.util.Arrays;

// For ground truth of column linkings.
public class TruthTable extends Table {
    private static final transient Gson GSON = new Gson();

    public int[] quantityToEntityColumnGroundTruth; // -1 means there is no connection.
    public int keyColumnGroundTruth = -1;

    public static TruthTable fromTable(Table t) {
        TruthTable truth = GSON.fromJson(GSON.toJson(t), TruthTable.class);
        truth.quantityToEntityColumnGroundTruth = new int[truth.nColumn];
        Arrays.fill(truth.quantityToEntityColumnGroundTruth, -1);
        return truth;
    }
}
