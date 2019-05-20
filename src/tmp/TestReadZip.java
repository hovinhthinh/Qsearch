package tmp;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import util.FileUtils;
import util.JSchUtils;

import java.nio.charset.StandardCharsets;

public class TestReadZip {
    public static void main(String[] args) throws Exception {
        ZipArchiveInputStream zipInput =
                new ZipArchiveInputStream(
//                        new FileInputStream("/home/hvthinh/datasets/BriQ-tableM/other.zip")
                        JSchUtils.getFileInputStreamFromServer("/GW/archive-6/projects/BriQ/data/TableM/pages_by_cat/other.zip")
                );

        ZipArchiveEntry entry;
//        PrintWriter out = FileUtils.getPrintWriter("/home/hvthinh/datasets/BriQ-tableM/health_filtered.gz", "UTF-8");
        int nDoc = 0;
        int nPart = 0;
        while ((entry = zipInput.getNextZipEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            ++nPart;
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
        System.out.println("-------------------------------------");
        System.out.println("nPart: " + nPart);
        System.out.println("nDoc: " + nDoc);
        JSchUtils.stop();
    }
}
