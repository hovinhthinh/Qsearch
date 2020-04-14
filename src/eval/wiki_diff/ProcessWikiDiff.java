package eval.wiki_diff;

import com.google.gson.Gson;
import data.background.mention2entity.Mention2EntityPrior;
import eval.TruthTable;
import model.table.Table;
import model.table.link.EntityLink;
import pipeline.*;
import util.FileUtils;

import java.io.PrintWriter;
import java.util.Scanner;

public class ProcessWikiDiff {
    // Just the annotations of entities and quantities, there is no linking.
    public static TaggingPipeline getPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                new TimeTaggingNode(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode(0.3, 0.3)
        );
    }

    public static void main(String[] args) throws Exception {
        TaggingPipeline pipeline = getPipeline();

        Scanner in = new Scanner(System.in);

        Mention2EntityPrior mention2EntityPrior = new Mention2EntityPrior(1, 10);

        PrintWriter out = FileUtils.getPrintWriter("eval/wiki_diff/table+annotation.txt", "UTF-8");
        PrintWriter out2 = FileUtils.getPrintWriter("./tmp3", "UTF-8");
        int nGood = 0;
        Gson gson = new Gson();
        for (String line : FileUtils.getLineStream("eval/wiki_diff/table.txt", "UTF-8")) {
            TruthTable t = TruthTable.fromTable(gson.fromJson(line, Table.class));

            for (int i = 0; i < t.nHeaderRow; ++i) {
                for (int j = 0; j < t.nColumn; ++j) {
                    t.header[i][j].entityLinks.clear();
                }
            }
            for (int i = 0; i < t.nDataRow; ++i) {
                for (int j = 0; j < t.nColumn; ++j) {
                    EntityLink el = t.data[i][j].getRepresentativeEntityLink();
                    if (el != null) {
                        t.bodyEntityTarget[i][j] = el.target;
                        el.candidates = mention2EntityPrior.getCanditateEntitiesForMention(el.text);
                        if (el.candidates == null) {
                            t.data[i][j].entityLinks.clear();
                            t.bodyEntityTarget[i][j] = null;
                            t.data[i][j].resetCachedRepresentativeLink();
                        }
                    } else {
                        t.data[i][j].entityLinks.clear();
                        t.data[i][j].resetCachedRepresentativeLink();
                    }
                }
            }

            if (!pipeline.tag(t)) {
                System.out.println("Ignored: " + t._id);
                continue;
            }
            ++nGood;

            out.println(gson.toJson(t));
//            System.out.println(line);
            out2.println(t._id);
            out2.println("source: " + t.source);
            out2.println("caption: " + t.caption);
            out2.println(t.getTableContentPrintable(false, true, false));
            out2.println("Annotated:");
            out2.println(t.getTableContentPrintable(true, true, true));
            out2.println("--------------------------------------------------------------------------------");
            out2.flush();
//            String wait = in.nextLine();
        }
        System.out.println("nGood Tables: " + nGood);
        out.close();
    }
}
