package eval.equity;

import com.google.gson.Gson;
import data.background.mention2entity.Mention2EntityPrior;
import eval.TruthTable;
import model.table.link.EntityLink;
import pipeline.*;
import util.FileUtils;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ProcessEquity {
    // Just the annotations of entities and quantities, there is no linking.
    public static TaggingPipeline getPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                new TimeTaggingNode(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode(0.5, 0.5)
        );
    }

    public static void main(String[] args) throws Exception {
        TaggingPipeline pipeline = getPipeline();

        Scanner in = new Scanner(System.in);

        Mention2EntityPrior mention2EntityPrior = new Mention2EntityPrior(1, 10);

        PrintWriter out = FileUtils.getPrintWriter("eval/equity/dataset/AnnotatedTables-19092016/dataset_processed.json", "UTF-8");
        int nGood = 0;
        Gson gson = new Gson();
        for (String line : new FileUtils.LineStream(
//                new GzipCompressorInputStream(
//                JSchUtils.getFileInputStreamFromServer
                new FileInputStream
                        ("eval/equity/dataset/AnnotatedTables-19092016/dataset_ground.json"), StandardCharsets.UTF_8)) {

            TruthTable t;
            t = gson.fromJson(line, TruthTable.class);
            if (!pipeline.tag(t)) {
                System.out.println("Ignored: " + t._id);
                continue;
            }
            ++nGood;

            for (int i = 0; i < t.nDataRow; ++i) {
                for (int j = 0; j < t.nColumn; ++j) {
                    EntityLink el = t.data[i][j].getRepresentativeEntityLink();
                    if (el != null) {
                        el.candidates = mention2EntityPrior.getCanditateEntitiesForMention(el.text);
                    }
                }
            }

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
