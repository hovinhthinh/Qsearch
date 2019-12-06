package data.manual;

import com.google.gson.Gson;
import util.FileUtils;

import java.util.ArrayList;

public class TunePrecisionRecall {
    // args: <truth_tables> <from> <to> <step>
    public static void main(String[] args) {
        args = "./manual/column_linking_ground_truth_table 0 5 0.5".split(" ");
        double from = Double.parseDouble(args[1]);
        double to = Double.parseDouble(args[2]);
        double step = Double.parseDouble(args[3]);

        ArrayList<TruthTable> tables = new ArrayList<>();
        Gson gson = new Gson();

        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            tables.add(gson.fromJson(line, TruthTable.class));
        }

        // TODO: optional
        // Re-apply linking here.


        for (double threshold = from; threshold <= to; threshold += step) {
            int total = 0, predict = 0, truePredict = 0;

            for (TruthTable t : tables) {
                for (int i = 0; i < t.nColumn; ++i) {
                    if (t.quantityToEntityColumnGroundTruth[i] != -1) {
                        ++total;
                    }
                    if (t.quantityToEntityColumnScore[i] >= threshold) {
                        ++predict;
                        if (t.quantityToEntityColumn[i] == t.quantityToEntityColumnGroundTruth[i]) {
                            ++truePredict;
                        }
                    }
                }
            }

            System.out.println(String.format("Threshold: %.1f\tPrec: %.1f\tRec: %.1f", threshold, ((double) truePredict) / predict, ((double) truePredict) / total));
        }
    }
}
