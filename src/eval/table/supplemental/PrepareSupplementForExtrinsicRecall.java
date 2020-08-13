package eval.table.supplemental;

import eval.table.exp_2.recall.RecallQuery;
import org.json.JSONArray;
import org.json.JSONObject;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;

public class PrepareSupplementForExtrinsicRecall {
    public static void main(String[] args) {
        PrintWriter out = FileUtils.getPrintWriter("eval/table/supplemental_materials/extrinsic_eval/q-recall-177/q-recall-177.json");
        for (String line : FileUtils.getLineStream("eval/table/exp_2/recall/recall_query.json", "UTF-8")) {
            JSONObject o = new JSONObject(line);
            RecallQuery q = Gson.fromJson(line, RecallQuery.class);
            o.remove("sourceURL");
            JSONArray facts = o.getJSONArray("groundFacts");
            for (int i = 0; i < facts.length(); ++i) {
                facts.getJSONObject(i).remove("q");
            }
            out.println(o.toString());
        }
        out.close();
    }
}
