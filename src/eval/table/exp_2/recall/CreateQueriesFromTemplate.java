package eval.table.exp_2.recall;

import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import nlp.NLP;
import org.junit.Assert;
import util.FileUtils;
import util.Gson;
import util.Pair;

import java.io.PrintWriter;
import java.util.*;

public class CreateQueriesFromTemplate {
    public static void generate() {
        FileUtils.LineStream stream = FileUtils.getLineStream("eval/table/exp_2/recall/queries_groundtruth_template_curated.tsv");
        PrintWriter out = FileUtils.getPrintWriter("eval/table/exp_2/recall/recall_query.tsv");

        stream.readLine(); // ignore header
        int cnt = 0;
        QueryTemplate qt;
        while ((qt = QueryTemplate.read(stream)) != null) {
            for (RecallQuery f : qt.generate()) {
                out.println(f.toString());
                System.out.println(f.toString());
                ++cnt;
            }
        }
        out.close();
        System.out.println("total queries: " + cnt);
    }

    public static void loadFixedToJson() {
        FileUtils.LineStream stream = FileUtils.getLineStream("eval/table/exp_2/recall/recall_query.tsv");
        ArrayList<String> jsons = new ArrayList<>();
        int cnt = 0;
        RecallQuery q;
        while ((q = RecallQuery.read(stream)) != null) {
            jsons.add(Gson.toJson(q));
            System.out.println(q.full);
            ++cnt;
        }
        System.out.println("total queries: " + cnt);
        Collections.shuffle(jsons);
        PrintWriter out = FileUtils.getPrintWriter("eval/table/exp_2/recall/recall_query.json");
        for (String s : jsons) {
            out.println(s);
        }
        out.close();
    }

    public static void main(String[] args) {
//        generate();
        loadFixedToJson();
    }
}
