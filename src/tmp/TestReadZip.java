package tmp;

import com.google.gson.Gson;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import util.FileUtils;
import util.JSchUtils;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class TestReadZip {

    public static void main(String[] args) throws Exception {
        ZipArchiveInputStream zipInput =
                new ZipArchiveInputStream(new FileInputStream("/GW/D5data-10/hvthinh/BriQ-TableM/politics.zip"));

        ZipArchiveEntry entry;


        PrintWriter out = FileUtils.getPrintWriter("/GW/D5data-10/hvthinh/BriQ-TableM/politics_combined.gz", "UTF-8");
        Gson gson = new Gson();
        while ((entry = zipInput.getNextZipEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            System.out.println("Processing: " + entry.getName());
            for (String line : new FileUtils.LineStream(zipInput, StandardCharsets.UTF_8, false)) {
                out.println(line);
            }
        }
        out.close();
        JSchUtils.stop();
    }
}
