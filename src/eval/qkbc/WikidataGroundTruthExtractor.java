package eval.qkbc;


import model.quantity.kg.KgUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import util.*;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WikidataGroundTruthExtractor {
    static String WD_SPARQL_ENDPOINT = "https://query.wikidata.org/sparql?format=json&query=";
    static String WD_DUMP_ENDPOINT = "https://www.wikidata.org/wiki/Special:EntityData/{ENTRY}.json";


    public static class PredicateNumericalFact {
        public String e;
        public ArrayList<Pair<Double, String>> quantities;

        public PredicateNumericalFact() {
            quantities = new ArrayList<>();
        }
    }

    static ArrayList<PredicateNumericalFact> getGroundTruthData(String type, String predicate, String outputFile) throws UnsupportedEncodingException {
        Map<String, PredicateNumericalFact> loadedFacts = Objects.requireNonNullElse(loadPredicateGroundTruthFromFile(outputFile), new HashMap<>());

        String template = FileUtils.getContent("eval/qkbc/exp_1/wdt_groundtruth_queries/query_template");
        String query = template.replace("<TYPE>", type).replace("<PREDICATE>", predicate);

        System.out.println("Query:\r\n" + query);

        JSONObject result = new JSONObject(Crawler.getContentFromUrl(WD_SPARQL_ENDPOINT + URLEncoder.encode(query, "UTF-8")));

        ConcurrentLinkedQueue<JSONObject> queue = new ConcurrentLinkedQueue<>();
        result.getJSONObject("results").getJSONArray("bindings").forEach(e -> queue.add((JSONObject) e));

        Concurrent.runAndWait(() -> {
            JSONObject e;
            while ((e = queue.poll()) != null) {
                System.out.println("Processing: " + e);
                String entry = e.getJSONObject("e").getString("value").substring("http://www.wikidata.org/entity/".length());
                synchronized (loadedFacts) {
                    if (loadedFacts.containsKey(entry)) {
                        continue;
                    }
                }

                try {
                    String wdDump = Crawler.getContentFromUrl(WD_DUMP_ENDPOINT.replace("{ENTRY}", entry));
                    // check if the entry is found in YAGO
                    JSONObject o = new JSONObject(wdDump).getJSONObject("entities").getJSONObject(entry);
                    String yagoEntry;
                    try {
                        // use english wikipedia entry if available
                        yagoEntry = ("<" + o.getJSONObject("sitelinks").getJSONObject("enwiki")
                                .getString("title") + ">").replace(' ', '_');
                    } catch (Exception exp) {
                        continue;
                    }

                    o = o.getJSONObject("claims");
                    if (!o.has(predicate)) {
                        continue;
                    }

                    PredicateNumericalFact entityFacts = new PredicateNumericalFact();
                    entityFacts.e = yagoEntry;

                    JSONArray arr = o.getJSONArray(predicate);
                    for (int i = 0; i < arr.length(); ++i) {
                        JSONObject q = arr.getJSONObject(i).getJSONObject("mainsnak").getJSONObject("datavalue").getJSONObject("value");
                        Double qV = Double.parseDouble(q.getString("amount"));
                        String qU = "";
                        if (q.has("unit")) {
                            qU = q.getString("unit");
                            KgUnit u = KgUnit.getKgUnitFromWdEntry(qU.substring(qU.lastIndexOf("/") + 1));
                            if (u == null) {
                                continue;
                            }
                            qU = u.entity;
                        }
                        entityFacts.quantities.add(new Pair<>(qV, qU));
                    }

                    if (entityFacts.quantities.size() == 0) {
                        continue;
                    }

                    synchronized (loadedFacts) {
                        loadedFacts.put(entry, entityFacts);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.err.println("Err: " + entry);
                }
            }
        }, 8);

        PrintWriter out = FileUtils.getPrintWriter(outputFile, "UTF-8");
        for (PredicateNumericalFact f : loadedFacts.values()) {
            out.println(Gson.toJson(f));
        }
        out.close();
        return new ArrayList<>(loadedFacts.values());
    }

    public static Map<String, PredicateNumericalFact> loadPredicateGroundTruthFromFile(String inputFile) {
        try {
            Map<String, PredicateNumericalFact> map = new HashMap<>();
            for (String line : FileUtils.getLineStream(inputFile, "UTF-8")) {
                PredicateNumericalFact f = Gson.fromJson(line, PredicateNumericalFact.class);
                map.put(f.e, f);
            }
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        // building-height
//        getGroundTruthData("Q41176", "P2048", "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height");
        // mountain-elevation
//        getGroundTruthData("Q8502", "P2044", "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation");
        // stadium-capacity
//        getGroundTruthData("Q483110", "P1083", "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity");
        // river-length
//        getGroundTruthData("Q4022", "P2043", "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length");

    }
}
