package data.wikipedia;

import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONException;
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
public class Mention2EntityPriorText {
    public static HashMap<String, HashMap<String, Integer>> map = new HashMap<>();

    public static void add(String mention, String entity) {
        if (!map.containsKey(mention)) {
            map.put(mention, new HashMap<>());
        }
        HashMap<String, Integer> entityMap = map.get(mention);
        entityMap.put(entity, entityMap.getOrDefault(entity, 0) + 1);
    }

    // args: <wikipedia from Niko> <output>
    public static void main(String[] args) {
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");
        int skip = 0;
        final AtomicInteger nLine = new AtomicInteger(0);
        Monitor monitor = new Monitor("Mention2EntityPriorWikipediaText", -1, 10, null) {
            @Override
            public int getCurrent() {
                return nLine.get();
            }
        };
        monitor.start();
        for (String line : stream) {
            nLine.incrementAndGet();
            JSONObject json = new JSONObject(line);
            JSONObject arr = json.getJSONObject("entities");

            for (String id : arr.keySet()) {
                Object o = arr.get(id);
                try {

                    if (o instanceof String) {
                        String s = NLP.stripSentence((String) o);
                        add(s, id);
                    } else {
                        JSONArray ao = (JSONArray) o;
                        for (int i = 0; i < ao.length(); ++i) {
                            String s = NLP.stripSentence(ao.getString(i));
                            add(s, id);
                        }
                    }
                } catch (JSONException e) {
                    System.out.println("Skip: " + (++skip));
                    continue;
                }
            }
        }
        monitor.forceShutdown();

        System.out.println("Writing output.");
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");

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
