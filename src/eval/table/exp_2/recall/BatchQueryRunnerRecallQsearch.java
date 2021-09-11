package eval.table.exp_2.recall;

import com.google.common.util.concurrent.AtomicDouble;
import org.json.JSONObject;
import server.table.handler.search.SearchResult;
import shaded.org.apache.http.client.utils.URIBuilder;
import util.*;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchQueryRunnerRecallQsearch {
    public static final String END_POINT = "https://qsearch.mpi-inf.mpg.de";

    public static void run(String[] args) {
        final File outputFolder = args.length > 1 ? new File(args[1]) : null;
        if (outputFolder != null) {
            outputFolder.mkdirs();
        }

        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");
        SelfMonitor monitor = new SelfMonitor(BatchQueryRunnerRecallQsearch.class.getName(), -1, 30);
        monitor.start();
        Crawler.READ_TIME_OUT = 3000 * 1000;
        final AtomicDouble map = new AtomicDouble(0),
                mrr = new AtomicDouble(0),
                recall = new AtomicDouble(0),
                recall_10 = new AtomicDouble(0),
                map_10 = new AtomicDouble(0);
        final AtomicInteger cnt = new AtomicInteger(0);
        Concurrent.runAndWait(() -> {
            while (true) {
                String line;
                synchronized (stream) {
                    line = stream.readLine();
                }
                if (line == null) {
                    break;
                }
                RecallQuery q = Gson.fromJson(line, RecallQuery.class);
                ArrayList<String> groundtruth = new ArrayList<>();
                for (GroundFact f : q.groundFacts) {
                    groundtruth.add(f.entity);
                }
                try {
                    URIBuilder b = new URIBuilder(END_POINT + "/search");
                    b.addParameter("corpus", "[\"STICS\",\"NYT\",\"WIKIPEDIA\"]");
                    b.addParameter("ntop", q.groundFacts.size() + "");
                    b.addParameter("model", "EMBEDDING");
                    b.addParameter("full", q.full + "");
                    b.addParameter("lambda", "0.1");
                    b.addParameter("alpha", "3");
                    b.addParameter("groundtruth", Gson.toJson(groundtruth));

                    SearchResult r = Gson.fromJson(Crawler.getContentFromUrl(b.toString()), SearchResult.class);

                    map.addAndGet(r.AP);
                    map_10.addAndGet(r.AP_10);
                    mrr.addAndGet(r.RR);
                    recall.addAndGet(r.RECALL);
                    recall_10.addAndGet(r.RECALL_10);
                    cnt.incrementAndGet();
                    if (outputFolder != null) {
                        PrintWriter out = FileUtils.getPrintWriter(new File(outputFolder, r.encode()), StandardCharsets.UTF_8);
                        out.println(Gson.toJson(r));
                        out.close();
                    }
                    monitor.incAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Err: " + line);
                }
            }
        }, 6);

        monitor.forceShutdown();
        System.out.println(String.format("MAP_10: %.3f    MAP: %.3f    MRR: %.3f    RECALL_10: %.3f    RECALL: %.3f",
                map_10.get() / cnt.get(), map.get() / cnt.get(), mrr.get() / cnt.get(), recall_10.get() / cnt.get(), recall.get() / cnt.get()));
        System.out.flush();

    }

    // args: <inputFile> in format query[\tdomain]; <output-folder>
    public static void main(String[] args) throws Exception {
        args = "eval/table/exp_2/recall/recall_query.json eval/table/exp_2/annotation-recall-qsearch".split(" ");
        run(args);
    }
}

