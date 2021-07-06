package eval.qkbc;


import model.quantity.kg.KgUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import util.*;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class WikidataGroundTruthExtractor {
    static String WD_QUERY_TEMPLATE = "SELECT DISTINCT ?e WHERE {\n" +
            "  ?e (wdt:P31/(wdt:P279*)) wd:<TYPE>;\n" +
            "    wdt:<PREDICATE> ?q.\n" +
            "  ?wikiPage schema:about ?e;\n" +
            "    schema:isPartOf <https://en.wikipedia.org/>.\n" +
            "  SERVICE wikibase:label { bd:serviceParam wikibase:language \"[AUTO_LANGUAGE],en\". }\n" +
            "}";

    static String WD_SPARQL_ENDPOINT = "https://query.wikidata.org/sparql?format=json&query=";
    static String WD_DUMP_ENDPOINT = "https://www.wikidata.org/wiki/Special:EntityData/{ENTRY}.json";


    public static class PredicateNumericalFact {
        public String e;
        public String wdEntry;
        public ArrayList<Pair<Double, String>> quantities;

        public PredicateNumericalFact() {
            quantities = new ArrayList<>();
        }
    }

    static void downloadGroundTruthData(String type, String predicate, String outputFile) throws UnsupportedEncodingException {
        ArrayList<PredicateNumericalFact> loadedFacts = Objects.requireNonNullElse(loadPredicateGroundTruthFromFile(outputFile), new ArrayList<>());

        Map<String, PredicateNumericalFact> wdEntry2Fact = loadedFacts.stream().collect(Collectors.toMap(f -> f.wdEntry, f -> f));

        String query = WD_QUERY_TEMPLATE.replace("<TYPE>", type).replace("<PREDICATE>", predicate);

        System.out.println("Query:\r\n" + query);

        JSONObject result = new JSONObject(Crawler.getContentFromUrl(WD_SPARQL_ENDPOINT + URLEncoder.encode(query, "UTF-8")));

        ConcurrentLinkedQueue<JSONObject> queue = new ConcurrentLinkedQueue<>();
        result.getJSONObject("results").getJSONArray("bindings").forEach(e -> queue.add((JSONObject) e));

        Concurrent.runAndWait(() -> {
            JSONObject e;
            while ((e = queue.poll()) != null) {
                String entry = e.getJSONObject("e").getString("value").substring("http://www.wikidata.org/entity/".length());

                synchronized (wdEntry2Fact) {
                    if (wdEntry2Fact.containsKey(entry)) {
                        continue;
                    }
                }

                System.out.println("Processing: " + e);

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
                    entityFacts.wdEntry = entry;

                    JSONArray arr = o.getJSONArray(predicate);
                    for (int i = 0; i < arr.length(); ++i) {
                        JSONObject q = arr.getJSONObject(i).getJSONObject("mainsnak").getJSONObject("datavalue").getJSONObject("value");
                        Double qV = Double.parseDouble(q.getString("amount"));
                        if (!q.has("unit")) {
                            continue;
                        }
                        String qU = q.getString("unit");
                        if (qU.equals("1")) {
                            qU = "";
                        } else {
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
                        wdEntry2Fact.put(entry, entityFacts);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.err.println("Err: " + entry);
                }
            }
        }, 4);

        PrintWriter out = FileUtils.getPrintWriter(outputFile, "UTF-8");
        for (PredicateNumericalFact f : wdEntry2Fact.values()) {
            out.println(Gson.toJson(f));
        }
        out.close();
    }

    public static ArrayList<PredicateNumericalFact> loadPredicateGroundTruthFromFile(String inputFile) {
        try {
            ArrayList<PredicateNumericalFact> list = new ArrayList<>();
            for (String line : FileUtils.getLineStream(inputFile, "UTF-8")) {
                list.add(Gson.fromJson(line, PredicateNumericalFact.class));
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        // building-height
//        downloadGroundTruthData("Q41176", "P2048", "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-building_height");
        // mountain-elevation
//        downloadGroundTruthData("Q8502", "P2044", "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-mountain_elevation");
        // stadium-capacity
//        downloadGroundTruthData("Q483110", "P1083", "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-stadium_capacity");
        // river-length
//        downloadGroundTruthData("Q4022", "P2043", "eval/qkbc/exp_1/wdt_groundtruth_queries/groundtruth-river_length");
    }
}
