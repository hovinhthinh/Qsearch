package data.background.mention2entity;

import org.json.JSONArray;
import util.Pair;
import util.Triple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

// Map a mention to a list of YAGO entities along with their frequency.
public class Mention2EntityInfoLine {
    public String mention;
    public ArrayList<Triple<String, Integer, Double>> entityFreq;

    public Mention2EntityInfoLine(String mention, ArrayList<Pair<String, Integer>> entityFreq) {
        this.mention = mention;

        int totalFreq = entityFreq.stream().mapToInt(o -> o.second).sum();
        this.entityFreq = entityFreq.stream().map(o -> new Triple<>(o.first, o.second, ((double) o.second) / totalFreq))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.sort(this.entityFreq, (a, b) -> (b.second.compareTo(a.second)));
    }

    public Mention2EntityInfoLine() {
        this.mention = null;
        this.entityFreq = new ArrayList<>();
    }

    public String toLine() {
        JSONArray arr = new JSONArray();
        for (Triple<String, Integer, Double> p : entityFreq) {
            arr.put(new JSONArray().put(p.first).put(p.second));
        }
        return (mention + "\t" + arr.toString());
    }

    public static Mention2EntityInfoLine fromLine(String line) {
        Mention2EntityInfoLine m2e = new Mention2EntityInfoLine();
        try {
            String[] arr = line.split("\t");
            if (arr.length != 2) {
                throw new Exception("invalid line");
            }
            m2e.mention = arr[0];
            JSONArray json = new JSONArray(arr[1]);
            int totalFreq = 0;
            for (int i = 0; i < json.length(); ++i) {
                JSONArray o = json.getJSONArray(i);
                int freq = o.getInt(1);
                m2e.entityFreq.add(new Triple<>(o.getString(0), freq, null));
                totalFreq += freq;
            }
            for (Triple<String, Integer, Double> t : m2e.entityFreq) {
                t.third = ((double) t.second) / totalFreq;
            }
            Collections.sort(m2e.entityFreq, (a, b) -> (b.second.compareTo(a.second)));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return m2e;
    }
}
