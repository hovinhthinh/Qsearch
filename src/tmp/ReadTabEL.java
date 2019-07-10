package tmp;

import data.wikipedia.Wikipedia;
import model.table.Table;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import util.FileUtils;
import util.JSchUtils;

import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ReadTabEL {
    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        for (String line : new FileUtils.LineStream(new GzipCompressorInputStream(
                JSchUtils.getFileInputStreamFromServer
//                        new FileInputStream
                                ("/GW/D5data/hvthinh/TabEL/tables.json.gz")), StandardCharsets.UTF_8)) {
            JSONObject o = new JSONObject(line);
//            System.out.println("-----Link: " + "https://en.wikipedia.org/wiki/" + URLEncoder.encode(o.getString("pgTitle").replaceAll("\\s", "_")));
            Table t = Wikipedia.parseFromJSON(line);
            System.out.println(t.getTableContentPrintable());
            String s = in.nextLine();
        }
        JSchUtils.stop();


    }
}
