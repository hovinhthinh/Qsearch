package tmp;

import data.wikipedia.WIKIPEDIA;
import model.table.Table;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import util.FileUtils;
import util.JSchUtils;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ReadTabELInteractive {
    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        int n = 0;
        int passed = 0;
        for (String line : new FileUtils.LineStream(new GzipCompressorInputStream(
                JSchUtils.getFileInputStreamFromServer
//                new FileInputStream
        ("/GW/D5data-11/hvthinh/TabEL/TabEL.json.shuf.gz")), StandardCharsets.UTF_8)) {
            Table t = WIKIPEDIA.parseFromJSON(line);
            if (t == null) {
                continue;
            }
            ++passed;
            if (!t.containsNumericColumn()) {
                continue;
            }
            System.out.println(line);
            System.out.println("#numericTables/#total: " + (++n) + "/" + passed);
            System.out.println("source: " + t.source);
            System.out.println("caption: " + t.caption);
            System.out.println(t.getTableContentPrintable(true, true));
            System.out.println("--------------------------------------------------------------------------------");
            System.out.flush();
            String wait = in.nextLine();
        }
    }

}
