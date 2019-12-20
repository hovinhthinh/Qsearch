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

public class DeepScoringClient implements ScoringClientInterface {
    private BufferedReader in = null, err = null;
    private PrintWriter out = null;
    private Process p = null;

    public DeepScoringClient() {
        this(false, -1);
    }

    // if logErrStream is true, need to explicitly call System.exit(0) at the end of the main thread.
    // device: index of gpu device.
    public DeepScoringClient(boolean logErrStream, int device) {
        try {
            String mainCmd = "python3 -u predict.py -g";
            if (device >= 0) {
                mainCmd += " -d " + device;
            }
            String[] cmd = new String[]{
                    "/bin/sh", "-c",
                    mainCmd
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

    public static void benchmarking(ScoringClientInterface client) {
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
//        benchmarking(new DeepScoringClient(true, -1));
        benchmarking(new MultiThreadedDeepScoringClient(false, "0,0,0,1,1,1"));
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

    @Override
    public ArrayList<Double> getScores(List<String> entitiesDesc, String quantityDesc) {
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

    @Override
    public double getScore(String typeDesc, String quantityDesc) {
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