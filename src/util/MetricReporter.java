package util;

import java.util.LinkedHashMap;

public class MetricReporter {

    private String name;

    // avg
    private LinkedHashMap<String, Integer> avg_key2freq = new LinkedHashMap<>();
    private LinkedHashMap<String, Double> avg_key2precSum = new LinkedHashMap<>();


    public MetricReporter(String name) {
        this.name = "noname";
    }

    public MetricReporter() {
        this(MetricReporter.class.getName());
    }

    public void recordAverage(String key, double prec) {
        avg_key2freq.put(key, avg_key2freq.getOrDefault(key, 0) + 1);
        avg_key2precSum.put(key, avg_key2precSum.getOrDefault(key, 0.0) + prec);
    }

    public String getReportString() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== ").append("METRIC_REPORTER [").append(name).append("]").append(" ==========\r\n");
        // avg micro precision
        if (avg_key2freq.size() > 0) {
            sb.append("--- Average ---\r\n");
            for (String k : avg_key2freq.keySet()) {
                sb.append(String.format("    %s: %.2f\r\n", k, avg_key2precSum.get(k) / avg_key2freq.get(k) * 100));
            }
        }
        return sb.toString();
    }
}
