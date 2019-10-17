package data.wikipedia;

import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import nlp.NLP;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import util.FileUtils;
import util.Monitor;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Deprecated
public class Mention2EntityPrior {
    public static HashMap<String, HashMap<String, Integer>> map = new HashMap<>();

    public static void add(String mention, String entity) {
        entity = StringEscapeUtils.unescapeJava(entity);
        mention = NLP.stripSentence(mention);
        if (!map.containsKey(mention)) {
            map.put(mention, new HashMap<>());
        }
        HashMap<String, Integer> entityMap = map.get(mention);
        entityMap.put(entity, entityMap.getOrDefault(entity, 0) + 1);
    }

    // args: <wikipedia from Niko> <TabEL wikipedia tables> <output>
    public static void main(String[] args) {
        FileUtils.LineStream stream_1 = FileUtils.getLineStream(args[0], "UTF-8");
        int skip = 0;
        final AtomicInteger nLine = new AtomicInteger(0);
        Monitor monitor = new Monitor("Mention2EntityPrior", -1, 10, null) {
            @Override
            public int getCurrent() {
                return nLine.get();
            }
        };
        monitor.start();
        System.out.println("Processing: " + args[0]);
        for (String line : stream_1) {
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
        System.out.println("Processing: " + args[1]);
        FileUtils.LineStream stream_2 = FileUtils.getLineStream(args[1], "UTF-8");
        for (String line : stream_2) {
            nLine.incrementAndGet();
            Table table = WIKIPEDIA.parseFromJSON(line);
            if (table == null) {
                System.out.println("Skip: " + (++skip));
                continue;
            }
            try {
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

        monitor.forceShutdown();

        System.out.println("Writing output.");
        PrintWriter out = FileUtils.getPrintWriter(args[2], "UTF-8");

        for (Map.Entry<String, HashMap<String, Integer>> e : map.entrySet()) {
            ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>(e.getValue().entrySet());
            Collections.sort(list, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));
            JSONArray arr = new JSONArray();
            for (Map.Entry<String, Integer> ent : list) {
                arr.put(new JSONArray().put(ent.getKey()).put(ent.getValue()));
            }
            out.println(e.getKey() + "\t" + arr.toString());
        }
        out.close();
    }
}
