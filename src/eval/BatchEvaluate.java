package eval;

import util.ShellCommand;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BatchEvaluate {
    static String header = String.join("\t",
            "Joint_Weight",
            "H_Prior_Weight",
            "L_nTop_Related",
            "L_Context_Weight",
            "L_Type_Penalty",
            "Mic_ED",
            "Mac_CA");


    public static void main(String[] args) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        PrintWriter out = new PrintWriter("eval/equity/dataset/AnnotatedTables-19092016/tune_results_0.tsv", "UTF-8");
        out.println(header);
        ArrayList<Future> futures = new ArrayList<>();

        for (double joint_weight = 0; joint_weight <= 1; joint_weight += 0.1)
            for (double h_prior_weight = 0; h_prior_weight <= 1; h_prior_weight += 0.1)
                for (int l_ntop_related : Arrays.asList(1, 3, 5, 7, 10))
                    for (double l_context_weight : Arrays.asList(0.8, 0.9, 0.95, 1.0))
                        for (double l_type_penalty : Arrays.asList(0.8, 0.9, 1.0)) {
                            double final_joint_weight = joint_weight;
                            double final_h_prior_weight = h_prior_weight;
                            int final_l_ntop_related = l_ntop_related;
                            double final_l_context_weight = l_context_weight;
                            double final_l_type_penalty = l_type_penalty;
                            futures.add(executorService.submit(() -> {
                                String output = ShellCommand.execute(
                                        String.format("./run.sh 20G eval.Evaluate %.1f %.1f %d %.1f %.1f",
                                                final_joint_weight,
                                                final_h_prior_weight,
                                                final_l_ntop_related,
                                                final_l_context_weight,
                                                final_l_type_penalty)
                                );
                                String ED = "null", CA = "null";
                                for (String line : output.split("\n")) {
                                    line = line.trim();
                                    if (line.contains("microPrecEDOurs")) {
                                        ED = line.split(" ")[1];

                                    }
                                    if (line.contains("macroPrecCAOurs")) {
                                        CA = line.split(" ")[1];
                                    }
                                }
                                synchronized (out) {
                                    out.println(String.format("%.1f\t%.1f\t%d\t%.1f\t%.1f\t%s\t%s",
                                            final_joint_weight,
                                            final_h_prior_weight,
                                            final_l_ntop_related,
                                            final_l_context_weight,
                                            final_l_type_penalty,
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
    }
}
