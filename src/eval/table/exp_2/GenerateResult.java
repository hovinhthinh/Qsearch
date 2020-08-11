package eval.table.exp_2;

import org.junit.Assert;
import server.table.ResultInstance;
import server.table.handler.search.SearchResult;
import util.FileUtils;
import util.Gson;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class GenerateResult {
    // result for a single query.
    public static Result getResult(List<Tuple> tuples, int k) {

        Result r = new Result();

        int n = 0;
        int n_true = 0;
        int minGoodPos = -1;

        int x = tuples.stream().mapToInt(t -> t.result_pos).min().getAsInt();
        int y = tuples.stream().mapToInt(t -> t.result_pos).max().getAsInt();
        Assert.assertTrue(y - x + 1 == tuples.size());

        for (Tuple t : tuples) {
            if (t.result_pos <= k) {
                ++n;
                if (t.ok) {
                    ++n_true;
                    if (minGoodPos == -1 || t.result_pos < minGoodPos) {
                        minGoodPos = t.result_pos;
                    }
                }
            }
        }
        r.precision = ((double) n_true) / n;
        r.hit = n_true > 0 ? 1 : 0;
        r.mrr = minGoodPos == -1 ? 0 : ((double) 1 / minGoodPos);
        return r;
    }

    public static void main(String[] args) throws Exception {
        ArrayList<Tuple> tuples = new ArrayList<>();
        // read tuples.
        int count = 0;
        for (String line : FileUtils.getLineStream("./eval/text/exp_2/outputs/baseline_google_top10_output.txt", "UTF-8")) {
            ++count;
            if (count == 1) {
                continue;
            }
            String[] arr = line.split("\t");
            if (arr.length < 8) {
                System.out.println(line);
            }
            String x = arr[7];
            if (!x.equals("yes") & !x.equals("no")) {
                System.out.println("ERROR");
                return;
            }
            tuples.add(new Tuple(
                    "Q-100-GG", arr[6], Integer.parseInt(arr[0]), Integer.parseInt(arr[5]), x.equals("yes")
            ));
        }
        count = 0;
        for (String line : FileUtils.getLineStream("./eval/text/exp_2/outputs/EMBEDDING.txt", "UTF-8")) {
            ++count;
            if (count == 1) {
                continue;
            }
            String[] arr = line.split("\t");
            tuples.add(new Tuple(
                    "Q-100-Qs", arr[8], Integer.parseInt(arr[12]), Integer.parseInt(arr[14]), arr[5].endsWith(
                    "_relevant")
            ));
        }

        int query = 0;
        for (File f : new File("eval/table/exp_2/annotation-iswc-nolt/evaluator-1").listFiles()) {
            ++query;
            SearchResult r = Gson.fromJson(FileUtils.getContent(f, StandardCharsets.UTF_8), SearchResult.class);
            int pos = 0;
            for (ResultInstance ri : r.topResults) {
                ++pos;
                tuples.add(new Tuple(
                        "Q-100-TabQs", f.getName().substring(0, f.getName().indexOf("_")),
                        query, pos, ri.eval.equals("true")
                ));
            }
        }

        query = 0;
        for (File f : new File("eval/table/exp_2/annotation-iswc-nolt-RT/evaluator-1").listFiles()) {
            ++query;
            SearchResult r = Gson.fromJson(FileUtils.getContent(f, StandardCharsets.UTF_8), SearchResult.class);
            int pos = 0;
            for (ResultInstance ri : r.topResults) {
                ++pos;
                tuples.add(new Tuple(
                        "Q-100-TabQs-RS", f.getName().substring(0, f.getName().indexOf("_")),
                        query, pos, ri.eval.equals("true")
                ));
            }
        }

        query = 0;
        for (File f : new File("eval/table/exp_2/annotation-recall-qsearch").listFiles()) {
            ++query;
            server.text.handler.search.SearchResult r = Gson.fromJson(FileUtils.getContent(f, StandardCharsets.UTF_8), server.text.handler.search.SearchResult.class);
            int pos = 0;
            for (server.text.handler.search.SearchResult.ResultInstance ri : r.topResults) {
                ++pos;
                if (pos <= 10) {
                    tuples.add(new Tuple(
                            "Rec-Qs", "none",
                            query, pos, ri.eval.equals("true")
                    ));
                }
            }
        }

        query = 0;
        for (File f : new File("eval/table/exp_2/annotation-recall-nolt/template").listFiles()) {
            ++query;
            SearchResult r = Gson.fromJson(FileUtils.getContent(f, StandardCharsets.UTF_8), SearchResult.class);
            int pos = 0;
            for (ResultInstance ri : r.topResults) {
                ++pos;
                if (pos <= 10) {
                    tuples.add(new Tuple(
                            "Rec-TabQs", "none",
                            query, pos, ri.eval.equals("true")
                    ));
                }
            }
        }

        query = 0;
        for (File f : new File("eval/table/exp_2/annotation-recall-nolt-RT/template").listFiles()) {
            ++query;
            SearchResult r = Gson.fromJson(FileUtils.getContent(f, StandardCharsets.UTF_8), SearchResult.class);
            int pos = 0;
            for (ResultInstance ri : r.topResults) {
                ++pos;
                if (pos <= 10) {
                    tuples.add(new Tuple(
                            "Rec-TabQs-RS", "none",
                            query, pos, ri.eval.equals("true")
                    ));
                }
            }
        }

        // process.
        /*
        Map<String, List<Tuple>> groupByDomain = tuples.stream().collect(Collectors.groupingBy(t -> t.domain));
        for (String domain : groupByDomain.keySet()) {
            System.out.println("-----" + domain + "-----");
            Map<String, List<Tuple>> groupByModel =
                    groupByDomain.get(domain).stream().collect(Collectors.groupingBy(t -> t.model));
            System.out.print("\t");
            for (String model : groupByModel.keySet()) {
                System.out.print("\t" + model);
            }
            System.out.println();
            System.out.println("\t----------");
            for (int k : new int[]{1, 3, 5, 10}) {
                System.out.print("\tPrec." + k);
                for (String model : groupByModel.keySet()) {
                    Map<Integer, List<Tuple>> groupByQuery =
                            groupByModel.get(model).stream().collect(Collectors.groupingBy(t -> t.query));
                    Map<Integer, Result> result =
                            groupByQuery.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                                    e -> getResult(e.getValue(), k)));
                    System.out.printf("\t%.3f",
                            result.values().stream().mapToDouble(r -> r.precision).average().getAsDouble());
                }
                System.out.println();
            }
            System.out.println("\t----------");
            for (int k : new int[]{3, 5}) {
                System.out.print("\tHIT." + k);
                for (String model : groupByModel.keySet()) {
                    Map<Integer, List<Tuple>> groupByQuery =
                            groupByModel.get(model).stream().collect(Collectors.groupingBy(t -> t.query));
                    Map<Integer, Result> result =
                            groupByQuery.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                                    e -> getResult(e.getValue(), k)));
                    System.out.printf("\t%.3f",
                            result.values().stream().mapToDouble(r -> r.hit).average().getAsDouble());
                }
                System.out.println();
            }
            System.out.println("\t----------");
            System.out.print("\tMRR");
            for (String model : groupByModel.keySet()) {
                Map<Integer, List<Tuple>> groupByQuery =
                        groupByModel.get(model).stream().collect(Collectors.groupingBy(t -> t.query));
                Map<Integer, Result> result =
                        groupByQuery.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                                e -> getResult(e.getValue(), 10)));
                System.out.printf("\t%.3f", result.values().stream().mapToDouble(r -> r.mrr).average().getAsDouble());
            }
            System.out.println();
        }
        */
        System.out.println("-----" + "ALL" + "-----");
        Map<String, List<Tuple>> groupByModel =
                tuples.stream().collect(Collectors.groupingBy(t -> t.model));
        System.out.print("\t");
        for (String model : groupByModel.keySet()) {
            System.out.print("\t" + model);
        }
        System.out.println();
        System.out.println("\t----------");
        for (int k : new int[]{1, 3, 5, 10}) {
            System.out.print("\tPrec." + k);
            for (String model : groupByModel.keySet()) {
                Map<Integer, List<Tuple>> groupByQuery =
                        groupByModel.get(model).stream().collect(Collectors.groupingBy(t -> t.query));
                Map<Integer, Result> result =
                        groupByQuery.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                                e -> getResult(e.getValue(), k)));
                System.out.printf("\t%.3f",
                        result.values().stream().mapToDouble(r -> r.precision).average().getAsDouble());
            }
            System.out.println();
        }
        System.out.println("\t----------");
        for (int k : new int[]{3, 5}) {
            System.out.print("\tHIT." + k);
            for (String model : groupByModel.keySet()) {
                Map<Integer, List<Tuple>> groupByQuery =
                        groupByModel.get(model).stream().collect(Collectors.groupingBy(t -> t.query));
                Map<Integer, Result> result =
                        groupByQuery.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                                e -> getResult(e.getValue(), k)));
                System.out.printf("\t%.3f",
                        result.values().stream().mapToDouble(r -> r.hit).average().getAsDouble());
            }
            System.out.println();
        }
        System.out.println("\t----------");
        System.out.print("\tMRR");
        for (String model : groupByModel.keySet()) {
            Map<Integer, List<Tuple>> groupByQuery =
                    groupByModel.get(model).stream().collect(Collectors.groupingBy(t -> t.query));
            Map<Integer, Result> result =
                    groupByQuery.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                            e -> getResult(e.getValue(), 10)));
            System.out.printf("\t%.3f", result.values().stream().mapToDouble(r -> r.mrr).average().getAsDouble());
        }
        System.out.println();

    }

    static class Result {
        double precision, hit, mrr;
    }

    static class Tuple {
        public String model, domain;
        public int query;
        public int result_pos; // 1-based index
        public boolean ok;

        public Tuple(String model, String domain, int query, int result_pos, boolean ok) {
            this.model = model;
            this.domain = domain;
            this.query = query;
            this.result_pos = result_pos;
            this.ok = ok;
        }
    }
}
