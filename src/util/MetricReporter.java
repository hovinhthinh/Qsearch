package util;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;

public class MetricReporter {
    private String name;

    // count
    private LinkedHashMap<String, Integer> cnt_key2freq = new LinkedHashMap<>();
    // avg
    private LinkedHashMap<String, Integer> avg_key2freq = new LinkedHashMap<>();
    private LinkedHashMap<String, Double> avg_key2Sum = new LinkedHashMap<>();
    // micro avg
    private LinkedHashMap<String, Integer> microAvg_key2True = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> microAvg_key2Total = new LinkedHashMap<>();

    public MetricReporter(String name) {
        this.name = name == null ? "noname" : name;
    }

    public MetricReporter() {
        this(null);
    }

    public void recordCount(String key, int addedCount) {
        cnt_key2freq.put(key, cnt_key2freq.getOrDefault(key, 0) + addedCount);
    }

    public void recordCount(String key) {
        recordCount(key, 1);
    }

    public void recordAverage(String key, double prec) {
        avg_key2freq.put(key, avg_key2freq.getOrDefault(key, 0) + 1);
        avg_key2Sum.put(key, avg_key2Sum.getOrDefault(key, 0.0) + prec);
    }

    public void recordMicroAverage(String key, int localTrue, int localTotal) {
        microAvg_key2True.put(key, microAvg_key2True.getOrDefault(key, 0) + localTrue);
        microAvg_key2Total.put(key, microAvg_key2Total.getOrDefault(key, 0) + localTotal);
    }

    public void recordMicroAverage(String key, Pair<Integer, Integer> localTrueAndTotal) {
        microAvg_key2True.put(key, microAvg_key2True.getOrDefault(key, 0) + localTrueAndTotal.first);
        microAvg_key2Total.put(key, microAvg_key2Total.getOrDefault(key, 0) + localTrueAndTotal.second);
    }

    public boolean isEmpty() {
        return cnt_key2freq.size() == 0 && avg_key2freq.size() == 0 && microAvg_key2True.size() == 0;
    }

    private String getKeyValuePairsStr(ArrayList<Pair<String, String>> kv, int nKeyPerLine) {
        int keyWidth = 0, valueWidth = 0;
        for (Pair<String, String> p : kv) {
            keyWidth = Math.max(keyWidth, p.first.length());
            valueWidth = Math.max(valueWidth, p.second.length());
        }
        StringBuilder sb = new StringBuilder();
        String formatStr = "    %-" + keyWidth + "s: %-" + valueWidth + "s";

        int nLine = (kv.size() - 1) / nKeyPerLine + 1;
        for (int i = 0; i < nLine; ++i) {
            if (i > 0) {
                sb.append("\r\n");
            }
            for (int j = i; j < kv.size(); j += nLine) {
                Pair<String, String> p = kv.get(j);
                sb.append(String.format(formatStr, p.first, p.second));
            }
        }
        return sb.toString();
    }

    public String getReportString(int nKeyPerLine, boolean sortByKey) {

        StringBuilder sb = new StringBuilder();
        sb.append("========== ").append("METRIC_REPORTER [").append(name).append("]").append(" ==========");
        // count
        sb.append("\r\n");
        if (cnt_key2freq.size() > 0) {
            sb.append("--- Count ---\r\n");
            ArrayList<Pair<String, String>> kv = new ArrayList<>();
            Set<String> ks = cnt_key2freq.keySet();
            if (sortByKey) {
                ks = new TreeSet<>(ks);
            }
            for (String k : ks) {
                kv.add(new Pair<>(String.format("%s", k), String.format("%d", cnt_key2freq.get(k))));

            }
            sb.append(getKeyValuePairsStr(kv, nKeyPerLine));
        }
        // avg
        sb.append("\r\n");
        if (avg_key2freq.size() > 0) {
            sb.append("--- Average ---\r\n");
            ArrayList<Pair<String, String>> kv = new ArrayList<>();
            Set<String> ks = avg_key2freq.keySet();
            if (sortByKey) {
                ks = new TreeSet<>(ks);
            }
            for (String k : ks) {
                kv.add(new Pair<>(String.format("%s", k), String.format("%.2f", avg_key2Sum.get(k) / avg_key2freq.get(k) * 100)));
            }
            sb.append(getKeyValuePairsStr(kv, nKeyPerLine));

        }
        // micro avg
        sb.append("\r\n");
        if (microAvg_key2True.size() > 0) {
            sb.append("--- Micro Average ---\r\n");
            ArrayList<Pair<String, String>> kv = new ArrayList<>();
            Set<String> ks = microAvg_key2True.keySet();
            if (sortByKey) {
                ks = new TreeSet<>(ks);
            }
            for (String k : ks) {
                kv.add(new Pair<>(String.format("%s", k), String.format("%.2f", 1.0 * microAvg_key2True.get(k) / microAvg_key2Total.get(k) * 100)));
            }
            sb.append(getKeyValuePairsStr(kv, nKeyPerLine));
        }
        return sb.toString();
    }

    public String getReportString() {
        return getReportString(1, false);
    }

    public String getReportJsonString() {
        JSONObject obj = new JSONObject();

        JSONObject count = new JSONObject();
        for (String k : cnt_key2freq.keySet()) {
            count.put(k, cnt_key2freq.get(k));
        }
        obj.put("count", count);

        JSONObject average = new JSONObject();
        for (String k : avg_key2freq.keySet()) {
            average.put(k, avg_key2Sum.get(k) / avg_key2freq.get(k) * 100);
        }
        obj.put("average", average);

        JSONObject microAverage = new JSONObject();
        for (String k : microAvg_key2True.keySet()) {
            microAverage.put(k, 1.0 * microAvg_key2True.get(k) / microAvg_key2Total.get(k) * 100);
        }
        obj.put("microAverage", microAverage);

        return obj.toString();
    }
}
