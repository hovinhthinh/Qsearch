package eval.wiki_diff;

import com.google.gson.Gson;
import model.table.Table;
import util.FileUtils;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

public class GenerateFromIds {
    public static void main(String[] args) {
        Set<String> idSet = new HashSet<>();
        for (String line : FileUtils.getLineStream("eval/wiki_diff/table.id.txt")) {
            idSet.add(line);
        }
        Gson gson = new Gson();

        PrintWriter out = FileUtils.getPrintWriter("eval/wiki_diff/table.txt", "UTF-8");
        for (String line : FileUtils.getLineStream("data/wiki+tablem_annotation+linking.gz")) {
            Table t = gson.fromJson(line, Table.class);
            if (!idSet.contains(t._id)) {
                continue;
            }
            out.println(line);
        }
        out.close();
    }
}
