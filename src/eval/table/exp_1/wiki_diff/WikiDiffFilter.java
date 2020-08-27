package eval.table.exp_1.wiki_diff;

import model.table.Table;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;
import java.util.*;

@Deprecated
public class WikiDiffFilter {
    static List<String> blockStr = Arrays.asList("Capacity", "Attendance");

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        PrintWriter out = FileUtils.getPrintWriter("./tmp2", "UTF-8");
        ArrayList<Table> tbs = new ArrayList<>();
        int cnt = 0;
        loop:
        for (String line : FileUtils.getLineStream("data/wiki+tablem_annotation+linking.gz")) {
            Table t = Gson.fromJson(line, Table.class);
            if (!t._id.startsWith("WIKIPEDIA")) {
                continue;
            }
            // apply filters.

            int nEntityCols = 0;
            int nQuantityCols = 0;
            int firstEntityCol = -1;
            for (int i = 0; i < t.nColumn; ++i) {
                if (t.isEntityColumn[i]) {
                    ++nEntityCols;
                    if (firstEntityCol == -1) {
                        firstEntityCol = i;
                    }
                }
                if (t.isNumericColumn[i]) {
                    ++nQuantityCols;
                }
            }
            if (nEntityCols < 2) {
                continue;
            }
            boolean flag = false;
            for (int i = 0; i < t.nColumn; ++i) {
                if (t.isNumericColumn[i] && t.quantityToEntityColumn[i] != firstEntityCol && t.quantityToEntityColumnScore[i] >= 0.8 && t.quantityToEntityColumnScore[i] < 0.9) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                continue;
            }

            for (String s : blockStr) {
                if (line.contains(s)) {
                    continue loop;
                }

            }
            tbs.add(t);
        }

        Collections.shuffle(tbs);
        for (Table t : tbs) {
            out.println(t._id);
            out.println(t.source.replace("WIKIPEDIA:Link:", ""));
            out.println(t.caption);
            out.println(t.getTableContentPrintable(false, true, true));
        }

        System.out.println(tbs.size());
        out.close();
    }
}
