package tmp;

import com.google.gson.Gson;
import data.tablem.TABLEM;
import data.tablem.TABLEM_DeepTaggingPipeline;
import model.table.Table;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import pipeline.TaggingPipeline;
import util.FileUtils;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ReadTableMInteractive {
    // args: /GW/D5data-10/hvthinh/BriQ-TableM/health_combined.gz
    public static void main(String[] args) throws Exception {
        TaggingPipeline pipeline = TABLEM_DeepTaggingPipeline.getAnnotationPipeline();

        Scanner in = new Scanner(System.in);
        int n = 0;
        int passed = 0;
        Gson gson = new Gson();
        for (String line : new FileUtils.LineStream(new GzipCompressorInputStream(
//                JSchUtils.getFileInputStreamFromServer
                new FileInputStream
                        (args[0])), StandardCharsets.UTF_8)) {
            ++passed;
            if (passed % 1000 == 0) {
                System.out.println("#processed: " + passed);
            }
            Table t;
//            t = gson.fromJson(line, Table.class);
            t = TABLEM.parseFromJSON(line);
            if (t == null) {
                continue;
            }
            if (!pipeline.tag(t)) {
                continue;
            }

//            System.out.println(line);
            System.out.println("#numericTables/#total: " + (++n) + "/" + passed);
            System.out.println("source: " + t.source);
            System.out.println("caption: " + t.caption);
            System.out.println("pageTitle: " + t.pageTitle);
            System.out.println(t.getTableContentPrintable(false, true));
            System.out.println("Annotated:");
            System.out.println(t.getTableContentPrintable(true, true));
            System.out.println("--------------------------------------------------------------------------------");
            System.out.flush();
            String wait = in.nextLine();
        }
    }
}
