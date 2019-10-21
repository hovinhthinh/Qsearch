package data.background.mention2entity;

import data.wikipedia.WIKIPEDIA;
import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import nlp.NLP;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import util.FileUtils;
import util.Monitor;
import util.Pair;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class Mention2EntityPriorGenerator {
    public static HashMap<String, HashMap<String, Integer>> mention2EntityMapCaseSensitive = new HashMap<>();
    public static HashMap<String, HashMap<String, Integer>> mention2EntityMapCaseInsensitive = new HashMap<>();
    public static int skip = 0;
    public static final AtomicInteger nLine = new AtomicInteger(0);

    public static void add(String mention, String entity) {
        entity = StringEscapeUtils.unescapeJava(entity);
        mention = NLP.stripSentence(mention);
        // adding sensitive
        if (!mention2EntityMapCaseSensitive.containsKey(mention)) {
            mention2EntityMapCaseSensitive.put(mention, new HashMap<>());
        }
        HashMap<String, Integer> entityMap = mention2EntityMapCaseSensitive.get(mention);
        entityMap.put(entity, entityMap.getOrDefault(entity, 0) + 1);
        // adding insensitive
        mention = mention.toLowerCase();
        if (!mention2EntityMapCaseInsensitive.containsKey(mention)) {
            mention2EntityMapCaseInsensitive.put(mention, new HashMap<>());
        }
        entityMap = mention2EntityMapCaseInsensitive.get(mention);
        entityMap.put(entity, entityMap.getOrDefault(entity, 0) + 1);
    }

    public static void processWikipediaPages(String filePath) {
        System.out.println("Processing: " + filePath);
        FileUtils.LineStream stream = FileUtils.getLineStream(filePath, "UTF-8");
        for (String line : stream) {
            nLine.incrementAndGet();
            try {
                JSONObject json = new JSONObject(line);
                JSONObject arr = json.getJSONObject("entities");
                for (String id : arr.keySet()) {
                    Object o = arr.get(id);
                    if (o instanceof String) {
                        add((String) o, id);
                    } else {
                        JSONArray ao = (JSONArray) o;
                        for (int i = 0; i < ao.length(); ++i) {
                            add(ao.getString(i), id);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Skip: " + (++skip));
                continue;
            }
        }
    }

    public static void processWikipediaTables(String filePath) {
        System.out.println("Processing: " + filePath);
        FileUtils.LineStream stream = FileUtils.getLineStream(filePath, "UTF-8");
        for (String line : stream) {
            nLine.incrementAndGet();
            try {
                Table table = WIKIPEDIA.parseFromJSON(line);
                for (Cell[][] part : new Cell[][][]{table.header, table.data}) {
                    for (int i = 0; i < part.length; ++i) {
                        for (int j = 0; j < part[i].length; ++j) {
                            for (EntityLink e : part[i][j].entityLinks) {
                                if (!e.target.startsWith("WIKIPEDIA:INTERNAL:")) {
                                    continue;
                                }
                                add(e.text, "<" + e.target.substring(19) + ">");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Skip: " + (++skip));
                continue;
            }
        }
    }

    // args: </GW/D5data-11/hvthinh/WIKIPEDIA-niko/fixedWikipediaEntitiesJSON.gz> </GW/D5data-11/hvthinh/TabEL/TabEL.json.shuf.gz> <output>
    // output 2 files: <output>.case-sensitive <output>.case-insensitive
    public static void main(String[] args) {
        Monitor monitor = new Monitor("Mention2EntityPrior", -1, 10, null) {
            @Override
            public int getCurrent() {
                return nLine.get();
            }
        };

        monitor.start();
        processWikipediaPages(args[0]);
        processWikipediaTables(args[1]);
        monitor.forceShutdown();

        System.out.println("Writing output.");
        PrintWriter out = FileUtils.getPrintWriter(args[2] + ".case-sensitive", "UTF-8");
        for (Map.Entry<String, HashMap<String, Integer>> e : mention2EntityMapCaseSensitive.entrySet()) {
            ArrayList<Pair<String, Integer>> list = e.getValue().entrySet().stream().map(o -> new Pair(o.getKey(), o.getValue())).collect(Collectors.toCollection(ArrayList::new));
            Collections.sort(list, (o1, o2) -> o2.second.compareTo(o1.second));
            Mention2EntityInfoLine m2e = new Mention2EntityInfoLine(e.getKey(), list);
            out.println(m2e.toLine());
        }
        out.close();

        out = FileUtils.getPrintWriter(args[2] + ".case-insensitive", "UTF-8");
        for (Map.Entry<String, HashMap<String, Integer>> e : mention2EntityMapCaseInsensitive.entrySet()) {
            ArrayList<Pair<String, Integer>> list = e.getValue().entrySet().stream().map(o -> new Pair(o.getKey(), o.getValue())).collect(Collectors.toCollection(ArrayList::new));
            Collections.sort(list, (o1, o2) -> o2.second.compareTo(o1.second));
            Mention2EntityInfoLine m2e = new Mention2EntityInfoLine(e.getKey(), list);
            out.println(m2e.toLine());
        }
        out.close();
    }
}
