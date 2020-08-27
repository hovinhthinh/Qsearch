package eval.table.exp_1.wiki_random;

import data.table.background.mention2entity.Mention2EntityPrior;
import eval.table.TruthTable;
import model.table.link.EntityLink;
import pipeline.table.*;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;
import java.util.Scanner;

public class ProcessWikiRandom {
    // Just the annotations of entities and quantities, there is no linking.
    public static TaggingPipeline getPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                TimeTaggingNode.getDefaultInstance(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode(0.3, 0.3)
        );
    }

    public static void main(String[] args) throws Exception {
        TaggingPipeline pipeline = getPipeline();

        Scanner in = new Scanner(System.in);

        Mention2EntityPrior mention2EntityPrior = new Mention2EntityPrior(1, 10);

        PrintWriter out = FileUtils.getPrintWriter("eval/table/exp_1/wiki_random/wiki_random_annotation.gz", "UTF-8");
        int nGood = 0;
        for (String line : FileUtils.getLineStream("eval/table/exp_1/wiki_random/wiki_random_ground.gz", "UTF-8")) {

            TruthTable t;
            t = Gson.fromJson(line, TruthTable.class);

            for (int i = 0; i < t.nDataRow; ++i) {
                for (int j = 0; j < t.nColumn; ++j) {
                    EntityLink el = t.data[i][j].getRepresentativeEntityLink();
                    if (el != null) {
                        el.candidates = mention2EntityPrior.getCanditateEntitiesForMention(el.text);
                        if (el.candidates == null) {
                            t.data[i][j].entityLinks.clear();
                            t.bodyEntityTarget[i][j] = null;
                            t.data[i][j].resetCachedRepresentativeLink();
                        }
                    }
                }
            }

            if (!pipeline.tag(t)) {
                System.out.println("Ignored: " + t._id);
                continue;
            }
            ++nGood;

            out.println(Gson.toJson(t));
//            System.out.println(line);
//            System.out.println("source: " + t.source);
//            System.out.println("caption: " + t.caption);
//            System.out.println(t.getTableContentPrintable(false, true, false));
//            System.out.println("Annotated:");
//            System.out.println(t.getTableContentPrintable(true, true, true));
//            System.out.println("--------------------------------------------------------------------------------");
//            System.out.flush();
//            String wait = in.nextLine();
        }
        System.out.println("nGood Tables: " + nGood);
        out.close();
    }
}
