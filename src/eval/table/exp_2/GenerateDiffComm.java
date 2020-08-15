package eval.table.exp_2;

import model.quantity.QuantityConstraint;
import model.query.SimpleQueryParser;
import nlp.NLP;
import org.junit.Assert;
import server.table.ResultInstance;
import server.table.handler.search.SearchResult;
import util.FileUtils;
import util.Gson;
import util.Pair;
import util.Triple;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class GenerateDiffComm {
    public static void compare(Map<String, Pair<Set<String>, Integer>> tabqsMap, Map<String, Pair<Set<String>, Integer>> qsMap) {
        double qsDiff = 0, tabqsDiff = 0, qsDiffT = 0, tabqsDiffT = 0;
        Assert.assertTrue(tabqsMap.size() == qsMap.size());
        for (String key : tabqsMap.keySet()) {
            Set<String> a = tabqsMap.get(key).first, b = qsMap.get(key).first;
            int ca = 0, cb = 0;
            for (String x : a) {
                if (!b.contains(x)) {
                    ca++;
                }
            }
            for (String x : b) {
                if (!a.contains(x)) {
                    cb++;
                }
            }
            tabqsDiffT += ca;
            tabqsDiff += tabqsMap.get(key).second;
            qsDiffT += cb;
            qsDiff += qsMap.get(key).second;
        }
        System.out.println(String.format("diffQs: %.3f diffTabQs: %.3f",
                qsDiffT / qsDiff,
                tabqsDiffT / tabqsDiff
        ));
    }

    public static void run_1() {
        String qsFolder = "/home/hvthinh/TabQs/eval/table/exp_2/recall_150/annotation-recall-qsearch";
        String tabqsFolder = "/home/hvthinh/TabQs/eval/table/exp_2/recall_150/annotation-recall-nolt-RT";

        Map<String, Pair<Set<String>, Integer>> qsMap = new HashMap<>(), tabqsMap = new HashMap<>();

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
            tabqsMap.put(r.fullQuery, new Pair<>(res, r.topResults.size()));
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
            qsMap.put(r.fullQuery, new Pair<>(res, r.topResults.size()));
        }

        compare(tabqsMap, qsMap);
    }


    public static void run_2() {
        String tabqsFolder = "/home/hvthinh/TabQs/eval/table/exp_2/annotation-iswc-nolt-RT/evaluator-1";

        Map<String, Pair<Set<String>, Integer>> qsMap = new HashMap<>(), tabqsMap = new HashMap<>();

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
            tabqsMap.put(r.fullQuery, new Pair<>(res, r.topResults.size()));
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
            qsMap.put(key, new Pair<>(res, queries.get(key).size()));
        }
        compare(tabqsMap, qsMap);

    }

    public static void main(String[] args) {
        run_1();
        run_2();
    }
}
