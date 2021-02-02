package model.quantity.kg;

import org.json.JSONArray;
import org.json.JSONObject;
import util.Crawler;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.*;

// Jan 2021
public class ConstructKgUnitCollectionFromWikidata {
    static String WD_SPARQL_ENDPOINT = "https://query.wikidata.org/sparql?format=json&query=";
    static String WD_DUMP_ENDPOINT = "https://www.wikidata.org/wiki/Special:EntityData/{ENTRY}.json";

    static String WDU_DUMP_FILE = "/tmp/wdu-entries";
    static String KG_UNIT_COLLECTION_FILE = "./resources/kgu/kg-unit-collection.json";

    static void loadUnitsFromWikidata() throws Exception {
        PrintWriter out = FileUtils.getPrintWriter(WDU_DUMP_FILE, "UTF-8");

        // SI units
        String query = """
                SELECT DISTINCT ?unit
                WHERE
                {
                  # ?unit wdt:P5061 ?symbol . # having 'unit symbol'
                  ?unit wdt:P2370 ?si . # having 'conversion to SI unit'
                  ?unit wdt:P111 ?concept . # having 'measured physical quantity'
                  # ?wikiUnit schema:about ?unit; schema:isPartOf <https://en.wikipedia.org/> .
                  # ?wikiConcept schema:about ?concept; schema:isPartOf <https://en.wikipedia.org/> .
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "[AUTO_LANGUAGE],en". }
                }
                """;
        JSONObject result = new JSONObject(Crawler.getContentFromUrl(WD_SPARQL_ENDPOINT + URLEncoder.encode(query, "UTF-8")));

        result.getJSONObject("results").getJSONArray("bindings").forEach(e -> {
            String entry = ((JSONObject) e).getJSONObject("unit").getString("value").substring("http://www.wikidata.org/entity/".length());
            String wdDump = Crawler.getContentFromUrl(WD_DUMP_ENDPOINT.replace("{ENTRY}", entry));
            // check if the entry is found in YAGO
            JSONObject o = new JSONObject(wdDump);
            String yagoEntry;
            try {
                // use english wikipedia entry if available
                yagoEntry = ("<" +
                        o.getJSONObject("entities").getJSONObject(entry).getJSONObject("sitelinks").getJSONObject("enwiki")
                                .getString("title") + ">").replace(' ', '_');
            } catch (Exception exp) {
                // otherwise use english label + wikidata entry
                try {
                    yagoEntry = ("<" +
                            o.getJSONObject("entities").getJSONObject(entry).getJSONObject("labels").getJSONObject("en")
                                    .getString("value") + "_wd:" + entry + ">").replace(' ', '_');
                } catch (Exception exp2) {
                    return;
                }
            }
            out.println(yagoEntry + "\t" + entry + "\t" + wdDump);
        });

        // Currency
        query = """
                SELECT DISTINCT ?unit
                WHERE
                {
                  ?unit wdt:P5061 ?symbol . # having 'unit symbol'
                  ?unit wdt:P31 wd:Q8142 . # having 'instance of' 'currency'
                  FILTER NOT EXISTS {
                    ?unit wdt:P582 ?endTime
                  }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "[AUTO_LANGUAGE],en". }
                }
                """;
        result = new JSONObject(Crawler.getContentFromUrl(WD_SPARQL_ENDPOINT + URLEncoder.encode(query, "UTF-8")));

        result.getJSONObject("results").getJSONArray("bindings").forEach(e -> {
            String entry = ((JSONObject) e).getJSONObject("unit").getString("value").substring("http://www.wikidata.org/entity/".length());
            String wdDump = Crawler.getContentFromUrl(WD_DUMP_ENDPOINT.replace("{ENTRY}", entry));
            // check if the entry is found in enwiki
            JSONObject o = new JSONObject(wdDump);
            try {
                String yagoEntry = ("<" +
                        o.getJSONObject("entities").getJSONObject(entry).getJSONObject("sitelinks").getJSONObject("enwiki")
                                .getString("title") + ">").replace(' ', '_');
                out.println(yagoEntry + "\t" + entry + "\t" + wdDump);
            } catch (Exception exp) {
                return;
            }
        });

        out.close();
    }

    static void constructUnitCollection() throws Exception {
        ArrayList<KgUnit> units = new ArrayList<>();

        // SI units
        for (String line : FileUtils.getLineStream(WDU_DUMP_FILE, "UTF-8")) {
            KgUnit unit = new KgUnit();

            String[] arr = line.split("\t");
            unit.entity = arr[0];
            unit.wdEntry = arr[1];

            JSONObject o = new JSONObject(arr[2]).getJSONObject("entities").getJSONObject(unit.wdEntry).getJSONObject("claims");
            if (!o.has("P2370")) {
                continue;
            }

            try {
                JSONObject si = o.getJSONArray("P2370").getJSONObject(0).getJSONObject("mainsnak").getJSONObject("datavalue").getJSONObject("value");
                unit.siUnit = si.getString("unit");
                unit.siUnit = unit.siUnit.substring(unit.siUnit.lastIndexOf("/") + 1);
                unit.conversionToSI = Double.parseDouble(si.getString("amount"));
//                if (si.has("upperBound") && Double.parseDouble(si.getString("upperBound")) != unit.conversionToSI) {
//                    continue;
//                }
//                if (si.has("lowerBound") && Double.parseDouble(si.getString("lowerBound")) != unit.conversionToSI) {
//                    continue;
//                }
            } catch (Exception e) {
                continue;
            }

            // symbols
            unit.symbols = new ArrayList<>();
            if (o.has("P5061")) {
                JSONArray sb = o.getJSONArray("P5061");
                for (int i = 0; i < sb.length(); ++i) {
                    try {
                        JSONObject v = sb.getJSONObject(i).getJSONObject("mainsnak").getJSONObject("datavalue").getJSONObject("value");
                        String lang = v.getString("language");
                        if (lang.equals("en") || lang.equals("mul")) {
                            unit.symbols.add(v.getString("text"));
                        }
                    } catch (Exception e) {
                    }
                }
            }

            // retrieve concept wiki entity from the first entry online.
            JSONArray cc = o.getJSONArray("P111");
            unit.measuredConcepts = new ArrayList<>();
            for (int i = 0; i < cc.length(); ++i) {
                try {
                    String concept = cc.getJSONObject(i).getJSONObject("mainsnak").getJSONObject("datavalue").getJSONObject("value").getString("id");
                    o = new JSONObject(Crawler.getContentFromUrl(WD_DUMP_ENDPOINT.replace("{ENTRY}", concept)));
                    try {
                        // use english wikipedia entry if available
                        concept = ("<" + o.getJSONObject("entities").getJSONObject(concept).getJSONObject("sitelinks")
                                .getJSONObject("enwiki").getString("title") + ">").replace(' ', '_');
                    } catch (Exception e) {
                        // otherwise use english label + wikidata entry
                        concept = ("<" + o.getJSONObject("entities").getJSONObject(concept).getJSONObject("labels")
                                .getJSONObject("en").getString("value") + "_wd:" + concept + ">").replace(' ', '_');
                    }
                    unit.measuredConcepts.add(concept);
                } catch (Exception e) {
                }
            }

            units.add(unit);
        }

        // now filter all units whose SI unit is also available in YAGO
        HashMap<String, String> wdEntry2yagoEntry = new HashMap<>();
        for (KgUnit unit : units) {
            wdEntry2yagoEntry.put(unit.wdEntry, unit.entity);
        }
        for (KgUnit unit : units) {
            unit.siUnit = wdEntry2yagoEntry.get(unit.siUnit);
        }
        do {
            boolean ok = true;
            ArrayList<KgUnit> newUnits = new ArrayList<>();
            for (KgUnit unit : units) {
                if (unit.siUnit != null) {
                    newUnits.add(unit);
                } else {
                    ok = false;
                }
            }
            units = newUnits;
            if (ok) {
                break;
            }
            HashSet<String> unitSet = new HashSet<>();
            for (KgUnit unit : units) {
                unitSet.add(unit.entity);
            }
            for (KgUnit unit : units) {
                if (!unitSet.contains(unit.siUnit)) {
                    unit.siUnit = null;
                }
            }
        } while (true);
        Collections.sort(units, (a, b) -> a.siUnit.compareTo(b.siUnit));

        // Currency
        for (String line : FileUtils.getLineStream(WDU_DUMP_FILE, "UTF-8")) {
            KgUnit unit = new KgUnit();

            String[] arr = line.split("\t");
            unit.entity = arr[0];
            unit.wdEntry = arr[1];
            unit.measuredConcepts = new ArrayList<>(Arrays.asList("<Currency>"));

            JSONObject o = new JSONObject(arr[2]).getJSONObject("entities").getJSONObject(unit.wdEntry).getJSONObject("claims");
            if (o.has("P2370")) {
                continue;
            }
            unit.isCurrency = true;

            // symbols
            JSONArray sb = o.getJSONArray("P5061");
            unit.symbols = new ArrayList<>();
            for (int i = 0; i < sb.length(); ++i) {
                try {
                    JSONObject v = sb.getJSONObject(i).getJSONObject("mainsnak").getJSONObject("datavalue").getJSONObject("value");
                    String lang = v.getString("language");
                    if (lang.equals("en") || lang.equals("mul")) {
                        unit.symbols.add(v.getString("text"));
                    }
                } catch (Exception e) {
                }
            }

            units.add(unit);
        }

        // Datasize
        String query = """
                SELECT DISTINCT ?unit ?scaleAmount
                WHERE
                {                
                  ?unit wdt:P111 wd:Q71699827 . # information
                  ?unit p:P2442 ?scaleStm . # conversion to standard unit
                  ?scaleStm psv:P2442 ?scaleVal .
                  ?scaleVal wikibase:quantityUnit wd:Q8799 . # byte
                  ?scaleVal wikibase:quantityAmount ?scaleAmount .                  
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "[AUTO_LANGUAGE],en". }
                }
                """;
        JSONArray result = new JSONObject(Crawler.getContentFromUrl(WD_SPARQL_ENDPOINT + URLEncoder.encode(query, "UTF-8"))).getJSONObject("results").getJSONArray("bindings");
        for (int k = 0; k < result.length(); ++k) {
            JSONObject e = result.getJSONObject(k);
            String entry = e.getJSONObject("unit").getString("value").substring("http://www.wikidata.org/entity/".length());
            String wdDump = Crawler.getContentFromUrl(WD_DUMP_ENDPOINT.replace("{ENTRY}", entry));
            // check if the entry is found in YAGO
            JSONObject o = new JSONObject(wdDump);

            KgUnit unit = new KgUnit();
            String yagoEntry;
            try {
                // use english wikipedia entry if available
                yagoEntry = ("<" +
                        o.getJSONObject("entities").getJSONObject(entry).getJSONObject("sitelinks").getJSONObject("enwiki")
                                .getString("title") + ">").replace(' ', '_');
            } catch (Exception exp) {
                // otherwise use english label + wikidata entry
                try {
                    yagoEntry = ("<" +
                            o.getJSONObject("entities").getJSONObject(entry).getJSONObject("labels").getJSONObject("en")
                                    .getString("value") + "_wd:" + entry + ">").replace(' ', '_');
                } catch (Exception exp2) {
                    return;
                }
            }
            unit.entity = yagoEntry;
            unit.wdEntry = entry;
            unit.siUnit = "<Byte>";
            unit.measuredConcepts = new ArrayList<>(Arrays.asList("<information_wd:Q71699827>"));
            unit.conversionToSI = Double.parseDouble(e.getJSONObject("scaleAmount").getString("value"));
            // symbols
            unit.symbols = new ArrayList<>();
            if (o.has("P5061")) {
                JSONArray sb = o.getJSONArray("P5061");
                for (int i = 0; i < sb.length(); ++i) {
                    try {
                        JSONObject v = sb.getJSONObject(i).getJSONObject("mainsnak").getJSONObject("datavalue").getJSONObject("value");
                        String lang = v.getString("language");
                        if (lang.equals("en") || lang.equals("mul")) {
                            unit.symbols.add(v.getString("text"));
                        }
                    } catch (Exception exp) {
                    }
                }
            }
            units.add(unit);
        }

        // Fuel consumption
        // km/L
        KgUnit unit = new KgUnit();
        unit.entity = "<kilometre_per_litre_wd:Q93871856>";
        unit.wdEntry = "Q93871856";
        unit.conversionToSI = 2.3521458;
        unit.siUnit = "<miles_per_gallon_wd:Q93868873>";
        unit.symbols = new ArrayList<>(Arrays.asList("km/L"));
        unit.measuredConcepts = new ArrayList<>(Arrays.asList("<fuel_economy_in_automobiles_wd:Q931507>"));
        units.add(unit);
        // mpg
        unit = new KgUnit();
        unit.entity = "<miles_per_gallon_wd:Q93868873>";
        unit.wdEntry = "Q93868873";
        unit.conversionToSI = 1.0;
        unit.siUnit = "<miles_per_gallon_wd:Q93868873>";
        unit.symbols = new ArrayList<>(Arrays.asList("mpg"));
        unit.measuredConcepts = new ArrayList<>(Arrays.asList("<fuel_economy_in_automobiles_wd:Q931507>"));
        units.add(unit);

        PrintWriter out = FileUtils.getPrintWriter(KG_UNIT_COLLECTION_FILE, "UTF-8");
        out.println(Gson.toJson(units));
        out.close();
        out = FileUtils.getPrintWriter(KG_UNIT_COLLECTION_FILE.replaceFirst("\\.json$", ".tsv"), "UTF-8");
        for (KgUnit u : units) {
            out.println(String.format("%s\t%s\t%s\t%s\t%s\t%s", u.entity, u.wdEntry,
                    String.join(";", u.measuredConcepts), String.join(";", u.symbols),
                    u.conversionToSI, u.siUnit));
        }
        out.close();
    }

    public static void main(String[] args) throws Exception {
        loadUnitsFromWikidata();
        constructUnitCollection();
    }
}
