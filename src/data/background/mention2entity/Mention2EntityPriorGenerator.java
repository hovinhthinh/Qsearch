package data.background.mention2entity;

import data.wikipedia.WIKIPEDIA;
import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import nlp.NLP;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import util.FileUtils;
import util.Monitor;
import util.Pair;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class Mention2EntityPriorGenerator {
    public static HashMap<String, HashMap<String, Integer>> mention2EntityMapCaseSensitive = new HashMap<>();
    public static HashMap<String, HashMap<String, Integer>> mention2EntityMapCaseInsensitive = new HashMap<>();
    public static int skip = 0;
    public static final AtomicInteger nLine = new AtomicInteger(0);

    public static void addCaseSensitive(String mention, String entity) {
        // adding sensitive
        if (!mention2EntityMapCaseSensitive.containsKey(mention)) {
            mention2EntityMapCaseSensitive.put(mention, new HashMap<>());
        }
        HashMap<String, Integer> entityMap = mention2EntityMapCaseSensitive.get(mention);
        entityMap.put(entity, entityMap.getOrDefault(entity, 0) + 1);
    }

    public static void addCaseInsensitive(String mention, String entity) {
        // adding insensitive
        mention = mention.toLowerCase();
        if (!mention2EntityMapCaseInsensitive.containsKey(mention)) {
            mention2EntityMapCaseInsensitive.put(mention, new HashMap<>());
        }
        HashMap<String, Integer> entityMap = mention2EntityMapCaseInsensitive.get(mention);
        entityMap.put(entity, entityMap.getOrDefault(entity, 0) + 1);
    }

    public static void processWikipediaPages(String filePath) {
        System.out.println("Processing: " + filePath);
        FileUtils.LineStream stream = FileUtils.getLineStream(filePath, "UTF-8");
        for (String line : stream) {
            nLine.incrementAndGet();
            try {
                HashSet<String> uniqueSetCaseSensitive = new HashSet<>();
                HashSet<String> uniqueSetCaseInsensitive = new HashSet<>();
                JSONObject json = new JSONObject(line);
                JSONObject arr = json.getJSONObject("entities");
                for (String id : arr.keySet()) {
                    Object o = arr.get(id);
                    String entity = StringEscapeUtils.unescapeJava(id);
                    if (o instanceof String) {
                        String mention = NLP.stripSentence((String) o);
                        if (uniqueSetCaseSensitive.add(mention + "\t" + entity)) {
                            addCaseSensitive(mention, entity);
                        }
                        if (uniqueSetCaseInsensitive.add(mention.toLowerCase() + "\t" + entity)) {
                            addCaseInsensitive(mention, entity);
                        }
                    } else {
                        JSONArray ao = (JSONArray) o;
                        for (int i = 0; i < ao.length(); ++i) {
                            String mention = NLP.stripSentence(ao.getString(i));
                            if (uniqueSetCaseSensitive.add(mention + "\t" + entity)) {
                                addCaseSensitive(mention, entity);
                            }
                            if (uniqueSetCaseInsensitive.add(mention.toLowerCase() + "\t" + entity)) {
                                addCaseInsensitive(mention, entity);
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

    // assume entity do not repeat more than once in a single table.
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
                                String entity = StringEscapeUtils.unescapeJava("<" + e.target.substring(19) + ">");
                                addCaseSensitive(e.text, entity);
                                addCaseInsensitive(e.text, entity);
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

    public static void processWikiLinksDataset(String filePath) {
        System.out.println("Processing: " + filePath);
        FileUtils.LineStream stream = FileUtils.getLineStream(filePath, "UTF-8");

        String prefix_1 = "http://en.wikipedia.org/wiki/";
        String prefix_2 = "https://en.wikipedia.org/wiki/";
        HashSet<String> uniqueSetCaseSensitive = new HashSet<>();
        HashSet<String> uniqueSetCaseInsensitive = new HashSet<>();
        for (String line : stream) {
            nLine.incrementAndGet();
            try {
                if (line.startsWith("MENTION\t")) {
                    String[] arr = line.split("\t");
                    String mention = arr[1];
                    String entity = arr[3];
                    if (entity.startsWith(prefix_1)) {
                        entity = entity.substring(prefix_1.length());
                    } else if (entity.startsWith(prefix_2)) {
                        entity = entity.substring(prefix_2.length());
                    } else {
                        System.out.println("Ignore: " + line);
                        continue;
                    }
                    int p = entity.indexOf('#');
                    if (p != -1) {
                        entity = entity.substring(0, p);
                    }
                    entity = StringEscapeUtils.unescapeJava("<" + entity + ">");
                    if (uniqueSetCaseSensitive.add(mention + "\t" + entity)) {
                        addCaseSensitive(mention, entity);
                    }
                    if (uniqueSetCaseInsensitive.add(mention.toLowerCase() + "\t" + entity)) {
                        addCaseInsensitive(mention, entity);
                    }
                } else if (line.isEmpty()) {
                    uniqueSetCaseSensitive = new HashSet<>();
                    uniqueSetCaseInsensitive = new HashSet<>();
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Skip: " + (++skip));
                continue;
            }
        }
    }

    // args: /GW/D5data-11/hvthinh/WIKIPEDIA-niko/fixedWikipediaEntitiesJSON.gz /GW/D5data-11/hvthinh/TabEL/TabEL.json.shuf.gz /GW/D5data-11/hvthinh/wiki-links/data.gz <output>
    // output 2 files: <output>.case-sensitive <output>.case-insensitive
    public static void main(String[] args) {
        for (int i = 0; i < 3; ++i) {
            Assert.assertTrue(new File(args[0]).exists());
        }

        Monitor monitor = new Monitor("Mention2EntityPrior-ProcessedLines", -1, 10, null) {
            @Override
            public int getCurrent() {
                return nLine.get();
            }
        };

        monitor.start();
        processWikipediaPages(args[0]);
        processWikiLinksDataset(args[2]);
        processWikipediaTables(args[1]);
        monitor.forceShutdown();

        System.out.println("Writing output.");
        PrintWriter out = FileUtils.getPrintWriter(args[3] + ".case-sensitive.gz", "UTF-8");
        for (Map.Entry<String, HashMap<String, Integer>> e : mention2EntityMapCaseSensitive.entrySet()) {
            ArrayList<Pair<String, Integer>> list = e.getValue().entrySet().stream()
                    .map(o -> new Pair<>(o.getKey(), o.getValue()))
                    .sorted((o1, o2) -> o2.second.compareTo(o1.second))
                    .collect(Collectors.toCollection(ArrayList::new));

            out.println(new Mention2EntityInfoLine(e.getKey(), list).toLine());
        }
        out.close();

        out = FileUtils.getPrintWriter(args[3] + ".case-insensitive.gz", "UTF-8");
        for (Map.Entry<String, HashMap<String, Integer>> e : mention2EntityMapCaseInsensitive.entrySet()) {
            ArrayList<Pair<String, Integer>> list = e.getValue().entrySet().stream()
                    .map(o -> new Pair<>(o.getKey(), o.getValue()))
                    .sorted((o1, o2) -> o2.second.compareTo(o1.second))
                    .collect(Collectors.toCollection(ArrayList::new));

            out.println(new Mention2EntityInfoLine(e.getKey(), list).toLine());
        }
        out.close();
    }
}
