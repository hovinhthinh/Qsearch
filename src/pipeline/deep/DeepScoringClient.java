package pipeline.deep;

import org.apache.commons.collections4.map.LRUMap;
import org.json.JSONArray;
import org.json.JSONObject;
import util.SelfMonitor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class DeepScoringClient {
    private static final String SEPARATOR = "\t";
    private static final int CACHE_SIZE = 1000000;
    private BufferedReader in = null, err = null;
    private PrintWriter out = null;
    private Process p = null;

    private LRUMap<String, Double> cache = null;

    public DeepScoringClient() {
        this(true, false);
    }

    // if logErrStream is true, need to explicitly call System.exit(0) at the end of the main thread.
    public DeepScoringClient(boolean useCache, boolean logErrStream) {
        if (useCache) {
            cache = new LRUMap<>(CACHE_SIZE);
        }
        try {
            String[] cmd = new String[]{
                    "/bin/sh", "-c",
                    "python3 -u predict.py",
            };
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File("./deep"));
            p = pb.start();
            in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));

            if (logErrStream) {
                err = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));
                new Thread(() -> {
                    try {
                        String str;
                        while ((str = err.readLine()) != null) {
                            System.err.println(str);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
            }

            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)));
            String str;
            while (!(str = in.readLine()).equals("__ready_to_predict__")) {
                System.out.println(str);
            }
            System.out.println(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void benchmarking() {
        DeepScoringClient client = new DeepScoringClient(false, true);
        System.out.print("Single/Multiple (S/M) > ");
        String line = new Scanner(System.in).nextLine();
        SelfMonitor m = new SelfMonitor("DeepScoringClient_Performance", -1, 5);
        m.start();
        if (line.trim().equalsIgnoreCase("S")) {
            System.out.println("=== Test single call ===");
            for (; ; ) {
                client.getScore("stadium in europe", "spectator capacity");
                m.incAndGet();
            }
        } else {
            System.out.println("=== Test multiple calls ===");
            for (; ; ) {
                client.getScores(Arrays.asList("football team", "soccer stadium", "random entity description"), "spectator capacity");
                m.incAndGet();
            }
        }
    }

    public static void main(String[] args) {
        benchmarking();
//        DeepScoringClient client = new DeepScoringClient();
//        System.out.println(client.getScore("stadium in europe", "capacity"));
//        System.out.println(client.getScores(Arrays.asList("team", "stadium", "dog"), "capacity"));

        System.exit(0);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            in.close();
        } catch (Exception e) {
        }
        try {
            err.close();
        } catch (Exception e) {
        }
        try {
            out.close();
        } catch (Exception e) {
        }
        try {
            p.destroy();
        } catch (Exception e) {
        }
        super.finalize();
    }

    // return: <optimal position>\t<scores>
    public synchronized ArrayList<Double> getScores(List<String> entitiesDesc, String quantityDesc) {
        if (entitiesDesc.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Double> results = new ArrayList<>();

        List<String> newEntitiesDesc = new ArrayList<>();
        for (int i = 0; i < entitiesDesc.size(); ++i) {
            if (cache != null) {
                String key = String.format("%s%s%s", entitiesDesc.get(i), SEPARATOR, quantityDesc);
                if (cache.containsKey(key)) {
                    results.add(cache.get(key));
                    continue;
                }
            }
            results.add(null);
            newEntitiesDesc.add(entitiesDesc.get(i));
        }
        if (newEntitiesDesc.size() == 0) {
            return results;
        }

        JSONObject o = new JSONObject().put("quantity_desc", quantityDesc.toLowerCase())
                .put("type_desc", new JSONArray(newEntitiesDesc.stream().map(x -> x.toLowerCase()).collect(Collectors.toList())));
        out.println(o.toString());
        out.flush();
        try {
            ArrayList<Double> resultsFromPythonClient = new JSONArray(in.readLine()).toList().stream().map(x -> ((Double) x)).collect(Collectors.toCollection(ArrayList::new));
            int cur = 0;
            for (int i = 0; i < results.size(); ++i) {
                if (results.get(i) == null) {
                    results.set(i, resultsFromPythonClient.get(cur++));
                    if (cache != null) {
                        cache.put(String.format("%s%s%s", entitiesDesc.get(i), SEPARATOR, quantityDesc), results.get(i));
                    }
                }
            }
            return results;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public synchronized double getScore(String typeDesc, String quantityDesc) {
        String key = null;
        if (cache != null) {
            key = String.format("%s%s%s", typeDesc, SEPARATOR, quantityDesc);
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
        }
        JSONObject o = new JSONObject().put("quantity_desc", quantityDesc.toLowerCase()).put("type_desc", new JSONArray().put(typeDesc.toLowerCase()));
        out.println(o.toString());
        out.flush();
        try {
            double value = new JSONArray(in.readLine()).getDouble(0);
            if (cache != null) {
                cache.put(key, value);
            }
            return value;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
}