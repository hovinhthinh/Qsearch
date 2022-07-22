package eval.table.exp_2;

import org.junit.Assert;
import server.table.ResultInstance;
import server.table.handler.search.SearchResult;
import shaded.org.apache.http.client.utils.URIBuilder;
import util.*;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class BatchQueryRunner {
    public static final String END_POINT = "http://halimede:6993";

    // args: <inputFile> in format query[\tdomain]; <output-folder>
    public static void main(String[] args) throws Exception {
        args = "eval/table/exp_2/queries_iswc.txt eval/table/exp_2/tmp/".split(" ");

        String[] referFolders = new String[]{
//                "eval/table/exp_2/annotation-iswc_(old)/evaluator-1",
                "eval/table/exp_2/annotation-iswc-new",
                "eval/table/exp_2/annotation-iswc-no-ct",
                "eval/table/exp_2/annotation-iswc-no-sr",
                "eval/table/exp_2/annotation-iswc-no-tt",
                "eval/table/exp_2/annotation-iswc-no-rt",
//                "eval/table/exp_2/annotation-iswc-no-rs_(old)/evaluator-1",
        };

        File outputFolder = new File(args[1]);
        outputFolder.mkdirs();
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");
        SelfMonitor monitor = new SelfMonitor(BatchQueryRunner.class.getName(), -1, 30);
        monitor.start();
        Crawler.READ_TIME_OUT = 9000 * 1000;
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
                    b.addParameter("linking-threshold", "0");
                    b.addParameter("full", arr[0]);
                    b.addParameter("rescore", "false");

//                    b.addParameter("TOPIC_DRIFT_PENALTY", "0");
//                    b.addParameter("CAPTION_MATCH_WEIGHT", String.format("%.2f", 0.0));
//                    b.addParameter("TITLE_MATCH_WEIGHT", String.format("%.2f", 0.0));
//                    b.addParameter("DOM_HEADING_MATCH_WEIGHT", String.format("%.2f", 0.0));
//                    b.addParameter("SAME_ROW_MATCH_WEIGHT", String.format("%.2f", 0.0));
//                    b.addParameter("RELATED_TEXT_MATCH_WEIGHT", String.format("%.2f", 0.0));
//                    b.addParameter("QUANTITY_MATCH_WEIGHT", String.format("%.2f", 0.0));

                    SearchResult r = Gson.fromJson(Crawler.getContentFromUrl(b.toString()), SearchResult.class);

                    if (arr.length > 1) {
                        r.evalDomain = arr[1];
                    }

                    // Populate old annotation
                    for (String rF : referFolders) {
                        Assert.assertTrue(new File(rF).exists());
                        File referFile = new File(rF, r.encode());

                        if (referFile.exists()) {
                            SearchResult rr = Gson.fromJson(FileUtils.getContent(referFile, StandardCharsets.UTF_8), SearchResult.class);
                            HashMap<String, String> entity2Verdict = new HashMap<>();
                            for (ResultInstance ri : rr.topResults) {
                                if (ri.eval != null) {
                                    entity2Verdict.put(ri.entity, ri.eval);
                                }
                            }
                            for (ResultInstance ri : r.topResults) {
                                if (ri.eval == null) {
                                    ri.eval = entity2Verdict.get(ri.entity);
                                } else {
                                    String ground = entity2Verdict.get(ri.entity);
                                    if (ground != null && !ground.equals(ri.eval)) {
                                        System.err.println("IN-consistent: " + referFile.getAbsolutePath() + " --> " + ri.entity);
                                    }
                                }
                            }
                        } else {
                            System.err.println("file not found: " + referFile.getAbsolutePath());
                        }
                    }
                    // Done;

                    PrintWriter out = FileUtils.getPrintWriter(new File(outputFolder, r.encode()), StandardCharsets.UTF_8);
                    out.println(Gson.toJson(r));
                    out.close();
                    System.out.println("DONE: " + r.fullQuery);
                    monitor.incAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Err: " + line);
                }
            }
        }, 20);

        monitor.forceShutdown();
    }
}
