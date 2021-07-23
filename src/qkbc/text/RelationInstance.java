package qkbc.text;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import model.quantity.Quantity;
import org.json.JSONArray;
import org.json.JSONObject;
import storage.text.migrate.ChronicleMapQfactStorage;
import util.Pair;
import util.Triple;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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


    public String entity;
    public String quantity;
    public double quantityStdValue;
    public double score;

    public String kbcId;

    public transient boolean positive;

    public ArrayList<Integer> positiveIterIndices;
    public ArrayList<Integer> effectivePositiveIterIndices; // a subset of positiveIterIndices
    public ArrayList<Integer> sampledEffectivePositiveIterIndices; // a subset of effectivePositiveIterIndices, only contains facts outside groundtruth.

    public ArrayList<Integer> noiseIterIndices;
    public ArrayList<Integer> sampledNoiseIterIndices; // a subset of noiseIterIndices, only contains facts outside groundtruth.

    public Boolean groundtruth;
    public Boolean eval;

    // for LM baseline
    public Map<String, ArrayList<Double>> unit2TopRoBERTaValues;

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

    private static final AtomicInteger GROUNDTRUTH_COUNTER = new AtomicInteger();

    public static RelationInstance newRelationInstanceFromGroundTruth(String entity, double quantityStdValue) {
        return new RelationInstance(entity, null, quantityStdValue, -1, "GT" + GROUNDTRUTH_COUNTER.getAndIncrement());
    }

    public boolean isArtificial() {
        return score == -1 && kbcId.startsWith("AT");
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

    public String getYearCtx() {
        for (Triple<String, Long, Long> t : getTimeContext()) {
            try {
                Integer.parseInt(t.first);
                return t.first;
            } catch (Exception e) {
            }
        }
        return null;
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
