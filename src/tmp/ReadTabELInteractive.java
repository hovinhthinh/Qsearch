package tmp;

import com.google.gson.Gson;
import data.table.wikipedia.WIKIPEDIA;
import data.table.wikipedia.WIKIPEDIA_TaggingPipeline;
import model.table.Table;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import pipeline.TaggingPipeline;
import util.FileUtils;
import util.JSchUtils;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ReadTabELInteractive {
    public static void main(String[] args) throws Exception {
        TaggingPipeline pipeline = WIKIPEDIA_TaggingPipeline.getDefaultTaggingPipeline();

        Scanner in = new Scanner(System.in);
        int n = 0;
        int passed = 0;
        Gson gson = new Gson();
        for (String line : new FileUtils.LineStream(new GzipCompressorInputStream(
                JSchUtils.getFileInputStreamFromServer
//                new FileInputStream
        ("/GW/D5data-11/hvthinh/TabEL/TabEL.json.shuf.gz")), StandardCharsets.UTF_8)) {
            ++passed;
            if (passed % 1000 == 0) {
                System.out.println("#processed: " + passed);
            }
            Table t;
//            t = gson.fromJson(line, Table.class);
            t = WIKIPEDIA.parseFromJSON(line);
            if (t == null) {
                continue;
            }
            if (!pipeline.tag(t)) {
                continue;
            }

            System.out.println(line);
            System.out.println("#numericTables/#total: " + (++n) + "/" + passed);
            System.out.println("source: " + t.source);
            System.out.println("caption: " + t.caption);
            System.out.println(t.getTableContentPrintable(false, true, false));
            System.out.println("Annotated:");
            System.out.println(t.getTableContentPrintable(true, true, true));
            System.out.println("--------------------------------------------------------------------------------");
            System.out.flush();
            String wait = in.nextLine();
        }
    }
}
