package eval.table.exp_2;

import model.quantity.QuantityConstraint;
import model.query.SimpleQueryParser;
import nlp.NLP;
import org.junit.Assert;
import server.table.ResultInstance;
import server.table.handler.search.SearchResult;
import util.FileUtils;
import util.Gson;
import util.Triple;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class GenerateDiffComm {
    public static void compare(Map<String, Set<String>> tabqsMap, Map<String, Set<String>> qsMap) {
        double comm = 0;
        double qsDiff = 0, tabqsDiff = 0;
        Assert.assertTrue(tabqsMap.size() == qsMap.size());
        for (String key : tabqsMap.keySet()) {
            Set<String> a = tabqsMap.get(key), b = qsMap.get(key);
            if (b == null) {
                System.out.println(key);
            }
            for (String x : a) {
                if (b.contains(x)) {
                    comm++;
                } else {
                    tabqsDiff++;
                }
            }
            for (String x : b) {
                if (!a.contains(x)) {
                    qsDiff++;
                }
            }
        }
        System.out.println(String.format("comm: %.3f diffQs: %.3f diffTabQs: %.3f",
                comm / tabqsMap.size(),
                qsDiff / tabqsMap.size(),
                tabqsDiff / tabqsMap.size()
        ));
    }

    public static void run_1() {
        String qsFolder = "eval/table/exp_2/annotation-recall-qsearch";
        String tabqsFolder = "eval/table/exp_2/annotation-recall-nolt-RT/template";

        Map<String, Set<String>> qsMap = new HashMap<>(), tabqsMap = new HashMap<>();

        for (File f : new File(tabqsFolder).listFiles()) {
            SearchResult r = Gson.fromJson(FileUtils.getContent(f, StandardCharsets.UTF_8), SearchResult.class);
            int pos = 0;
            Set<String> res = new HashSet<>();
            for (ResultInstance ri : r.topResults) {
                ++pos;
                if (pos <= 10 && ri.eval.equals("true")) {
                    res.add(ri.entity);
                }
            }
            tabqsMap.put(r.fullQuery, res);
        }

        for (File f : new File(qsFolder).listFiles()) {
            server.text.handler.search.SearchResult r = Gson.fromJson(FileUtils.getContent(f, StandardCharsets.UTF_8), server.text.handler.search.SearchResult.class);
            int pos = 0;
            Set<String> res = new HashSet<>();
            for (server.text.handler.search.SearchResult.ResultInstance ri : r.topResults) {
                ++pos;
                if (pos <= 10 && ri.eval.equals("true")) {
                    res.add(ri.entity);
                }
            }
            qsMap.put(r.fullQuery, res);
        }

        compare(tabqsMap, qsMap);
    }


    public static void run_2() {
        String tabqsFolder = "/home/hvthinh/TabQs/eval/table/exp_2/annotation-iswc-nolt-RT/evaluator-1";

        Map<String, Set<String>> qsMap = new HashMap<>(), tabqsMap = new HashMap<>();


        for (File f : new File(tabqsFolder).listFiles()) {
            SearchResult r = Gson.fromJson(FileUtils.getContent(f, StandardCharsets.UTF_8), SearchResult.class);
            int pos = 0;
            Set<String> res = new HashSet<>();
            for (ResultInstance ri : r.topResults) {
                ++pos;
                if (pos <= 10 && ri.eval.equals("true")) {
                    res.add(ri.entity);
                }
            }
            r.evalDomain = null;
            tabqsMap.put(r.fullQuery, res);
        }

        ArrayList<String> lines = FileUtils.getLines("eval/table/exp_2/iswc-qsearch-adjusted.txt", "UTF-8");
        lines.remove(0);
        Map<String, List<String>> queries = lines.stream().collect(Collectors.groupingBy(o -> NLP.stripSentence(o.split("\t")[11])));

        for (String key : queries.keySet()) {
            Set<String> res = new HashSet<>();
            for (String line : queries.get(key)) {
                String arr[] = line.split("\t");
                if (arr[5].endsWith("_relevant")) {
                    res.add("<" + NLP.stripSentence(arr[13]).replace(' ', '_') + ">");
                }
            }
            qsMap.put(key, res);
        }
        compare(tabqsMap, qsMap);

    }

    public static void main(String[] args) {
        run_1();
        run_2();
    }
}
