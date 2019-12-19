package data;

import com.google.gson.Gson;
import model.table.Table;
import pipeline.DeepColumnScoringNode;
import pipeline.deep.DeepScoringClient;
import util.Concurrent;
import util.FileUtils;
import util.SelfMonitor;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

// Multithreaded neural-based column linking, designed for GPUs
public class ColumnLinkingExecutor {
    // Args: <input> <output> <device> <n_thread>
    // input is the output of the annotation pipeline, before the column linking node.
    public static void main(String[] args) {
//        args = "/GW/D5data-11/hvthinh/TABLEM/all/all+id.shuf.annotation.gz /GW/D5data-11/hvthinh/TABLEM/all/all+id.shuf.annotation+linking.gz 0,0,1,1,2,2,3,3 32".split("\\s++");

        String[] devices = args[2].split(",");

        System.out.println("Init clients");
        ArrayBlockingQueue<DeepScoringClient> clients = new ArrayBlockingQueue<>(devices.length);
        for (String d : devices) {
            clients.add(new DeepScoringClient(false, false, Integer.parseInt(d)));
        }

        System.out.println("Now processing.");
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        SelfMonitor m = new SelfMonitor(ColumnLinkingExecutor.class.getName(), -1, 60);
        m.start();

        Concurrent.runAndWait(new Runnable() {
            Gson gson = new Gson();
            DeepColumnScoringNode node = new DeepColumnScoringNode(DeepColumnScoringNode.JOINT_INFERENCE, clients);

            @Override
            public void run() {
                String line;
                while (true) {
                    synchronized (stream) {
                        line = stream.readLine();
                    }
                    if (line == null) {
                        return;
                    }
                    Table table = gson.fromJson(line, Table.class);
                    if (node.process(table)) {
                        synchronized (out) {
                            out.println(gson.toJson(table));
                        }
                    }
                    m.incAndGet();
                }

            }
        }, Integer.parseInt(args[3]));

        m.forceShutdown();
        out.close();
    }
}
