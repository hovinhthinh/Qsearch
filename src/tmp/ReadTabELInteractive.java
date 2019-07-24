package tmp;

import data.wikipedia.Wikipedia;
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
        for (String line : new FileUtils.LineStream(new GzipCompressorInputStream(
                JSchUtils.getFileInputStreamFromServer
//                new FileInputStream
        ("/GW/D5data-11/hvthinh/TabEL.json.shuf.gz")), StandardCharsets.UTF_8)) {
            Table t = Wikipedia.parseFromJSON(line);
            System.out.println(++n);
            System.out.println(t.source);
            System.out.println(t.caption);
            System.out.println(t.getTableContentPrintable(true));
            System.out.println("------------------------------------------------------------");
            String wait = in.nextLine();
        }
    }

}
