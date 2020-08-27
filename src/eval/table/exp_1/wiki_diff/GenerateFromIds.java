package eval.table.exp_1.wiki_diff;

import model.table.Table;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

@Deprecated
public class GenerateFromIds {
    public static void main(String[] args) {
        Set<String> idSet = new HashSet<>();
        for (String line : FileUtils.getLineStream("eval/table/exp_1/wiki_diff/table.id.txt")) {
            idSet.add(line);
        }

        PrintWriter out = FileUtils.getPrintWriter("eval/table/exp_1/wiki_diff/table.txt", "UTF-8");
        for (String line : FileUtils.getLineStream("data/wiki+tablem_annotation+linking.gz")) {
            Table t = Gson.fromJson(line, Table.class);
            if (!idSet.contains(t._id)) {
                continue;
            }
            out.println(line);
        }
        out.close();
    }
}
