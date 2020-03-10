package tmp;

import com.google.gson.Gson;
import model.table.Table;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import pipeline.TaggingPipeline;
import util.FileUtils;
import util.JSchUtils;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ReadTableMInteractive {
    // args: /GW/D5data-10/hvthinh/BriQ-TableM/health_combined.gz
    public static void main(String[] args) throws Exception {
        args = "/GW/D5data-11/hvthinh/TABLEM/all/all+id.shuf.annotation.gz".split(" ");
        TaggingPipeline pipeline = TaggingPipeline.getColumnLinkingPipeline();
        //TaggingPipeline pipeline = TABLEM_DeepTaggingPipeline.getDefaultTaggingPipeline();

        Scanner in = new Scanner(System.in);
        int n = 0;
        int passed = 0;
        Gson gson = new Gson();
        int k = 0;
        for (String line : new FileUtils.LineStream(
                new GzipCompressorInputStream(
//                JSchUtils.getFileInputStreamFromServer
                        new FileInputStream
                                (args[0]))
                , StandardCharsets.UTF_8)) {
            ++passed;

            Table t;
            t = gson.fromJson(line, Table.class);
            int x = 0, y = 0;
            for (int i = 0; i < t.nColumn; ++i) {
                if (t.isNumericColumn[i]) {
                    ++x;
                }
                if (t.isEntityColumn[i]) {
                    ++y;
                }
            }
            if (Math.pow(y, x) > 100) {
                ++k;
            }
            if (true) {
                continue;
            }

            System.out.println("#numericTables/#total: " + (++n) + "/" + passed);
//            System.out.println("keyColumn: " + t.keyColumnGroundTruth);
            System.out.println("source: " + t.source);
            System.out.println("pageTitle: " + t.pageTitle);
            System.out.println("caption: " + t.caption);
            System.out.println(t.getTableContentPrintable(false, true, true));

            long time = System.currentTimeMillis();
            boolean ok = pipeline.tag(t);
            time = System.currentTimeMillis() - time;
            System.out.println("#processed: " + passed + " " + ok + " " + time);
//            t = TABLEM.parseFromJSON(line);
            if (!ok) {
                continue;
            }

            System.out.println("Annotated:");
            System.out.println(t.getTableContentPrintable(true, true, true));
            System.out.println("--------------------------------------------------------------------------------");
            System.out.flush();
        }
        System.out.println(k);
    }
}
