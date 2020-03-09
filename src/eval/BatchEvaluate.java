package eval;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.*;

class EvaluateClient {
    private BufferedReader in = null, err = null;
    private PrintWriter out = null;
    private Process p = null;

    public EvaluateClient(String inputFile) {
        try {
            String mainCmd = "./run.sh 32G eval.Evaluate INTERACTIVE " + inputFile;
            String[] cmd = new String[]{
                    "/bin/sh", "-c",
                    mainCmd
            };
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File("./"));
            p = pb.start();
            in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));


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

            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)));
            String str;
            while (!(str = in.readLine()).equals("__ready_to_evaluate__")) {
//                System.out.println(str);
            }
            System.out.println(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JSONObject getResult(String configStr) {
        out.println(configStr);
        out.flush();
        try {
            String str;
            while (!(str = in.readLine()).startsWith("__interactive_result__")) {
//                System.out.println(str);
            }
            return new JSONObject(str.substring(str.indexOf("\t") + 1));
        } catch (IOException e1) {
            e1.printStackTrace();
            return null;
        } catch (NullPointerException e2) {
            e2.printStackTrace();
            System.out.println("Error on configStr: " + configStr);
            throw e2;
        }
    }

}

class MultiThreadedEvaluateClient {

    private ArrayBlockingQueue<EvaluateClient> clients;

    public MultiThreadedEvaluateClient(String inputFile, int nClients) {
        clients = new ArrayBlockingQueue<>(nClients);

        ExecutorService service = Executors.newFixedThreadPool(nClients);
        ArrayList<Future<EvaluateClient>> futureClients = new ArrayList<>();
        for (int i = 0; i < nClients; ++i) {
            futureClients.add(service.submit(() -> new EvaluateClient(inputFile)));
        }
        try {
            for (Future<EvaluateClient> f : futureClients) {
                clients.add(f.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        service.shutdown();
    }


    public JSONObject getResult(String configStr) {
        try {
            EvaluateClient evaluateClient = clients.take();
            JSONObject result = evaluateClient.getResult(configStr);
            clients.put(evaluateClient);
            return result;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

public class BatchEvaluate {
    static String header = String.join("\t",
            "Joint_Weight",
            "H_Prior_Weight",
            "H_Cooccur_Weight",
            "H_Context_Weight",
            "H_Agree_Weight",
            "L_nTop_Related",
            "L_Context_Weight",
            "L_Type_Penalty",
            "Mic_ED",
            "Mac_CA");


    public static void main(String[] args) throws Exception {
        String inputFile = "eval/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation_linking.json";
        int nClients = 24;

        MultiThreadedEvaluateClient client = new MultiThreadedEvaluateClient(inputFile, nClients);
        ExecutorService executorService = Executors.newFixedThreadPool(nClients);
        PrintWriter out = new PrintWriter("eval/equity/dataset/AnnotatedTables-19092016/tune_results_0_new.tsv", "UTF-8");
        out.println(header);
        ArrayList<Future> futures = new ArrayList<>();

        for (double joint_weight = 0.1; joint_weight <= 1; joint_weight += 0.1)
            for (double h_prior_weight = 0.35; h_prior_weight <= 0.85; h_prior_weight += 0.05)
                for (double h_cooccur_weight = 0; h_cooccur_weight <= 0; h_cooccur_weight += 0.05)
                    for (double h_context_weight = 0.1; h_context_weight <= Math.min(0.65, 1 - h_prior_weight - h_cooccur_weight); h_context_weight += 0.05)
                        for (int l_ntop_related : Arrays.asList(1, 3, 5, 7))
                            for (double l_context_weight : Arrays.asList(0.9))
                                for (double l_type_penalty : Arrays.asList(0.9, 0.95, 1.0)) {
                                    double final_joint_weight = joint_weight;
                                    double final_h_prior_weight = h_prior_weight;
                                    double final_h_cooccur_weight = h_cooccur_weight;
                                    double final_h_context_weight = h_context_weight;
                                    int final_l_ntop_related = l_ntop_related;
                                    double final_l_context_weight = l_context_weight;
                                    double final_l_type_penalty = l_type_penalty;
                                    futures.add(executorService.submit(() -> {
                                        String configStr = String.format("%.2f %.2f %.2f %.2f %.2f %d %.2f %.2f",
                                                final_joint_weight,
                                                final_h_prior_weight,
                                                final_h_cooccur_weight,
                                                final_h_context_weight,
                                                1 - final_h_prior_weight - final_h_cooccur_weight - final_h_context_weight,
                                                final_l_ntop_related,
                                                final_l_context_weight,
                                                final_l_type_penalty);

                                        JSONObject output = client.getResult(configStr);
                                        System.out.println(configStr + "\t" + output.toString());
                                        double ED = output.getJSONObject("microAverage").getDouble("microPrecEDOurs");
                                        double CA = output.getJSONObject("average").getDouble("macroPrecCAOurs");

                                        synchronized (out) {
                                            out.println(String.format("%s\t%.2f\t%.2f",
                                                    configStr.replace(' ', '\t'),
                                                    ED,
                                                    CA
                                            ));
                                            out.flush();
                                        }

                                    }));
                                }

        for (Future f : futures) {
            f.get();
        }
        out.close();
        executorService.shutdown();
        System.exit(0);
    }
}

