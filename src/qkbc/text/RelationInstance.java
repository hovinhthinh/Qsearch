package qkbc.text;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import model.quantity.Quantity;
import org.json.JSONArray;
import org.json.JSONObject;
import storage.text.migrate.ChronicleMapQfactStorage;
import util.Pair;
import util.Triple;

import java.util.ArrayList;

public class RelationInstance {
    private static int INDEXED_QFACT_CACHE_SIZE = 10000;
    private static Int2ObjectLinkedOpenHashMap<JSONArray> INDEXED_QFACT_TEXT_CACHE = new Int2ObjectLinkedOpenHashMap<>(INDEXED_QFACT_CACHE_SIZE);

    private static JSONObject getIndexedQfactFromText(String kbcId) {
        String[] args = kbcId.split("_");
        int eIdx = Integer.parseInt(args[0]);

        JSONArray qfact = INDEXED_QFACT_TEXT_CACHE.getAndMoveToFirst(eIdx);

        if (qfact == null) {
            qfact = ChronicleMapQfactStorage.get(ChronicleMapQfactStorage.SEARCHABLE_ENTITIES.get(eIdx));
            INDEXED_QFACT_TEXT_CACHE.putAndMoveToFirst(eIdx, qfact);
            if (INDEXED_QFACT_TEXT_CACHE.size() > INDEXED_QFACT_CACHE_SIZE) {
                INDEXED_QFACT_TEXT_CACHE.removeLast();
            }
        }

        return qfact.getJSONObject(Integer.parseInt(args[1]));
    }


    String entity;
    String quantity;
    double quantityStdValue;
    double score;

    String kbcId;

    boolean positive;

    ArrayList<Integer> positiveIterIndices, noiseIterIndices;

    public RelationInstance(String entity, String quantity, double quantityStdValue, double score, String kbcId) {
        this.entity = entity;
        this.quantity = quantity;
        this.quantityStdValue = quantityStdValue;
        this.score = score;

        this.kbcId = kbcId;

        this.positive = false;

        positiveIterIndices = new ArrayList<>();
        noiseIterIndices = new ArrayList<>();
    }

    public Quantity getQuantity() {
        return Quantity.fromQuantityString(quantity);
    }

    public String getSentence() {
        return getIndexedQfactFromText(kbcId).getString("sentence");
    }

    public String getSource() {
        return getIndexedQfactFromText(kbcId).getString("source");
    }

    public ArrayList<String> getNormalContext() {
        ArrayList<String> ctx = new ArrayList<>();
        getIndexedQfactFromText(kbcId).getJSONArray("context").forEach(o -> {
            String s = (String) o;
            if (s.startsWith("<E>:") || s.startsWith("<T>:")) {
                return;
            }
            ctx.add(s);
        });
        return ctx;
    }

    // Triple<surface, start, end>
    public ArrayList<Triple<String, Long, Long>> getTimeContext() {
        ArrayList<Triple<String, Long, Long>> ctx = new ArrayList<>();
        getIndexedQfactFromText(kbcId).getJSONArray("context").forEach(o -> {
            String s = (String) o;
            if (!s.startsWith("<T>:")) {
                return;
            }
            String[] args = s.substring(4).split("\t");

            ctx.add(new Triple<>(args[0], Long.parseLong(args[1]), Long.parseLong(args[2])));
        });
        return ctx;
    }

    // Triple<surface, entity>
    public ArrayList<Pair<String, String>> getEntityContext() {
        ArrayList<Pair<String, String>> ctx = new ArrayList<>();
        getIndexedQfactFromText(kbcId).getJSONArray("context").forEach(o -> {
            String s = (String) o;
            if (!s.startsWith("<E>:")) {
                return;
            }
            String[] args = s.substring(4).split("\t");

            ctx.add(new Pair<>(args[0], args[1]));
        });
        return ctx;
    }

    public static void main(String[] args) {
        System.out.println(getIndexedQfactFromText("0_0"));
    }
}
