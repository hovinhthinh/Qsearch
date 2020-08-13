package eval.table.supplemental;

import eval.table.TruthTable;
import model.table.Cell;
import org.json.JSONObject;
import util.FileUtils;
import util.Gson;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PrepareSupplementForIntrinsic {

    public static String processTruthTableStr(String str) {
        TruthTable t = Gson.fromJson(str, TruthTable.class);
        t.quantityToEntityColumn = null;
        t.quantityToEntityColumnScore = null;
        t.yusraBodyEntityTarget = null;
        t.majorUnitInColumn = null;
        t.isEntityColumn = null;
        t.isNumericColumn = null;
        t.moreInfo = null;
        for (Cell[][] p : new Cell[][][]{t.header, t.data}) {
            for (Cell[] r : p) {
                for (Cell c : r) {
                    c.quantityLinks = null;
                    c.entityLinks = null;
                    c.timeLinks = null;
                }
            }
        }
        JSONObject o = new JSONObject(Gson.toJson(t));
        o.remove("combinedHeader");
        o.remove("headerUnitSpan");
        return o.toString();
    }

    public static void main(String[] args) {
        Map<String, String> files = Stream.of(new Object[][]{
                {"eval/table/wiki_random/wiki_random_annotation_linking.json", "Wiki_Links-Random_Qt.json"},
                {"eval/table/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation_linking.json", "Equity_Qt"},
                {"eval/table/wiki_diff/table_ground_annotation_linking.gz", "Wiki_Diff"},
        }).collect(Collectors.toMap(data -> (String) data[0], data -> (String) data[1]));
        File outputFolder = new File("eval/table/supplemental_materials/intrinsic_eval");

        for (String file : files.keySet()) {
            File inputFile = new File(file);
            PrintWriter out = FileUtils.getPrintWriter(new File(outputFolder, files.get(file)), StandardCharsets.UTF_8);
            for (String line : FileUtils.getLineStream(inputFile, StandardCharsets.UTF_8)) {
                out.println(processTruthTableStr(line));
            }
            out.close();
        }
    }
}
