package util;

import java.util.LinkedHashMap;

public class MetricReporter {

    private String name;

    // avg
    private LinkedHashMap<String, Integer> avg_key2freq = new LinkedHashMap<>();
    private LinkedHashMap<String, Double> avg_key2Sum = new LinkedHashMap<>();
    // micro avg
    private LinkedHashMap<String, Integer> microAvg_key2True = new LinkedHashMap<>();
    private LinkedHashMap<String, Integer> microAvg_key2Total = new LinkedHashMap<>();

    public MetricReporter(String name) {
        this.name = "noname";
    }

    public MetricReporter() {
        this(MetricReporter.class.getName());
    }

    public void recordAverage(String key, double prec) {
        avg_key2freq.put(key, avg_key2freq.getOrDefault(key, 0) + 1);
        avg_key2Sum.put(key, avg_key2Sum.getOrDefault(key, 0.0) + prec);
    }

    public void recordMicroAverage(String key, int localTrue, int localTotal) {
        microAvg_key2True.put(key, microAvg_key2True.getOrDefault(key, 0) + localTrue);
        microAvg_key2Total.put(key, microAvg_key2Total.getOrDefault(key, 0) + localTotal);
    }

    public String getReportString() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== ").append("METRIC_REPORTER [").append(name).append("]").append(" ==========\r\n");
        // avg
        if (avg_key2freq.size() > 0) {
            sb.append("--- Average ---\r\n");
            for (String k : avg_key2freq.keySet()) {
                sb.append(String.format("    %s: %.2f\r\n", k, avg_key2Sum.get(k) / avg_key2freq.get(k) * 100));
            }
        }
        // micro avg
        if (microAvg_key2True.size() > 0) {
            sb.append("--- Micro Average ---\r\n");
            for (String k : microAvg_key2True.keySet()) {
                sb.append(String.format("    %s: %.2f\r\n", k, 1.0 * microAvg_key2True.get(k) / microAvg_key2Total.get(k) * 100));
            }
        }
        return sb.toString();
    }
}
