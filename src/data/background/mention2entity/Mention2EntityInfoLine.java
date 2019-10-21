package data.background.mention2entity;

import org.json.JSONArray;
import util.Pair;

import java.util.ArrayList;

public class Mention2EntityInfoLine {
    public String mention;
    public ArrayList<Pair<String, Integer>> entityFreq;

    public Mention2EntityInfoLine(String mention, ArrayList<Pair<String, Integer>> entityFreq) {
        this.mention = mention;
        this.entityFreq = entityFreq;
    }

    public Mention2EntityInfoLine() {
        this.mention = null;
        this.entityFreq = new ArrayList<>();
    }

    public String toLine() {
        JSONArray arr = new JSONArray();
        for (Pair<String, Integer> p : entityFreq) {
            arr.put(new JSONArray().put(p.first).put(p.second));
        }
        return (mention + "\t" + arr.toString());
    }

    public Mention2EntityInfoLine fromLine(String line) {
        Mention2EntityInfoLine m2e = new Mention2EntityInfoLine();
        try {
            String[] arr = line.split("\t");
            if (arr.length != 2) {
                throw new Exception("invalid line");
            }
            m2e.mention = arr[0];
            JSONArray json = new JSONArray(arr[1]);
            for (int i = 0; i < json.length(); ++i) {
                JSONArray o = json.getJSONArray(i);
                m2e.entityFreq.add(new Pair<>(o.getString(0), o.getInt(1)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return m2e;
    }
}
