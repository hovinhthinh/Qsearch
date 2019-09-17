package pipeline.deep;

import org.json.JSONArray;
import org.json.JSONObject;
import util.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
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

    public static void main(String[] args) {
        DeepScoringClient client = new DeepScoringClient();
        System.out.println(client.getScore("stadium in europe", "capacity"));
        System.out.println(client.getScore(Arrays.asList("team", "stadium", "dog"), "capacity"));
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
    public synchronized Pair<Integer, Double[]> getScore(List<String> entitiesDesc, String quantityDesc) {
        JSONObject o = new JSONObject().put("quantity_desc", quantityDesc).put("type_desc", new JSONArray(entitiesDesc));
        out.println(o.toString());
        out.flush();
        try {
            JSONObject response = new JSONObject(in.readLine());
            return new Pair<>(response.getInt("best_index"), response.getJSONArray("scores").toList().toArray(new Double[0]));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public synchronized double getScore(String typeDesc, String quantityDesc) {
        JSONObject o = new JSONObject().put("quantity_desc", quantityDesc).put("type_desc", new JSONArray().put(typeDesc));
        out.println(o.toString());
        out.flush();
        try {
            return new JSONObject(in.readLine()).getJSONArray("scores").getDouble(0);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
}