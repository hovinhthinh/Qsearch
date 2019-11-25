package tmp;

import com.google.gson.Gson;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import util.FileUtils;
import util.JSchUtils;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

// read zip data from yusra and save into combined format
public class TestReadZip {

    public static void main(String[] args) throws Exception {
        ZipArchiveInputStream zipInput =
                new ZipArchiveInputStream(new FileInputStream(args[0]));

        ZipArchiveEntry entry;


        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
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
    }
}
