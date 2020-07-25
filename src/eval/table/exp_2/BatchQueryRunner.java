package eval.table.exp_2;

import org.json.JSONObject;
import server.table.handler.search.SearchResult;
import shaded.org.apache.http.client.utils.URIBuilder;
import util.*;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class BatchQueryRunner {
    public static final String END_POINT = "http://halimede:6993";

    // args: <inputFile> in format query[\tdomain]; <output-folder>
    public static void main(String[] args) throws Exception {
        args = "eval/table/exp_2/queries_iswc.txt eval/table/exp_2/annotation-iswc".split(" ");

        File outputFolder = new File(args[1]);
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");
        SelfMonitor monitor = new SelfMonitor(BatchQueryRunner.class.getName(), -1, 30);
        monitor.start();
        Crawler.READ_TIME_OUT = 3000 * 1000;
        Concurrent.runAndWait(() -> {
            while (true) {
                String line;
                synchronized (stream) {
                    line = stream.readLine();
                }
                if (line == null) {
                    break;
                }
                String[] arr = line.split("\t");
                try {
                    URIBuilder b = new URIBuilder(END_POINT + "/search_table");
                    b.addParameter("corpus", "[\"TABLEM:Link\",\"WIKIPEDIA:Link\"]");
                    b.addParameter("ntop", "10");
                    b.addParameter("linking-threshold", "0.6");
                    b.addParameter("full", arr[0]);
                    String session = new JSONObject(Crawler.getContentFromUrl(b.toString())).getString("s");

                    b = new URIBuilder(END_POINT + "/session");
                    b.addParameter("s", session);
                    SearchResult r = Gson.fromJson(Crawler.getContentFromUrl(b.toString()), SearchResult.class);

                    if (arr.length > 1) {
                        r.evalDomain = arr[1];
                    }
                    PrintWriter out = FileUtils.getPrintWriter(new File(outputFolder, r.encode()), StandardCharsets.UTF_8);
                    out.println(Gson.toJson(r));
                    out.close();
                    monitor.incAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Err: " + line);
                }
            }
        }, 4);

        monitor.forceShutdown();
    }
}
