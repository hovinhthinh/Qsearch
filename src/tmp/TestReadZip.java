package tmp;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.json.JSONObject;
import util.FileUtils;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class TestReadZip {
    public static void main(String[] args) throws Exception {
        ZipArchiveInputStream zipInput =
                new ZipArchiveInputStream(new FileInputStream("/home/hvthinh/datasets/BriQ-tableM/health.zip"));
        ZipArchiveEntry entry;
//        PrintWriter out = FileUtils.getPrintWriter("/home/hvthinh/datasets/BriQ-tableM/health_filtered.gz", "UTF-8");
        int nDoc = 0;
        while ((entry = zipInput.getNextZipEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            System.out.println("Processing: " + entry.getName());
            for (String line : new FileUtils.LineStream(zipInput, StandardCharsets.UTF_8, false)) {
                ++nDoc;
//                try {
//                    JSONObject json = new JSONObject(line);
//                    if (json.has("fullText")) {
//                        json.remove("fullText");
//                    }
//                    if (json.has("termSet")) {
//                        json.remove("termSet");
//                    }
//                    if (json.has("relation")) {
//                        json.remove("relation");
//                    }
//                    if (json.has("title") && !json.getString("title").isEmpty()) {
//                        out.println(json.toString());
//                    }
//                } catch (Exception e) {
//                    System.err.println(line);
//                }
            }
        }
//        out.close();
        System.out.println("nDoc: " + nDoc);
    }
}
