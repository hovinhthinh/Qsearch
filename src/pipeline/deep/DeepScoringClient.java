package pipeline.deep;

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

    private BufferedReader in = null;
    private PrintWriter out = null;
    private Process p = null;

    public DeepScoringClient() {
        try {
            String[] cmd = new String[]{
                    "/bin/sh", "-c",
                    "python3 -u predict.py",
            };
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File("./deep"));
            p = pb.start();
            in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
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
        DeepScoringClient client = new DeepScoringClient();
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
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            in.close();
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
        JSONObject o = new JSONObject().put("quantity_desc", quantityDesc.toLowerCase())
                .put("type_desc", new JSONArray(entitiesDesc.stream().map(x -> x.toLowerCase()).collect(Collectors.toList())));
        out.println(o.toString());
        out.flush();
        try {
            return new JSONArray(in.readLine()).toList().stream().map(x -> ((Double) x)).collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public synchronized double getScore(String typeDesc, String quantityDesc) {
        JSONObject o = new JSONObject().put("quantity_desc", quantityDesc.toLowerCase()).put("type_desc", new JSONArray().put(typeDesc.toLowerCase()));
        out.println(o.toString());
        out.flush();
        try {
            return new JSONArray(in.readLine()).getDouble(0);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
}