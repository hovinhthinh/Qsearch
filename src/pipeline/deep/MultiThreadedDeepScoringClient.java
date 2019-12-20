package pipeline.deep;

import util.SelfMonitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class MultiThreadedDeepScoringClient implements ScoringClientInterface {

    private ArrayBlockingQueue<DeepScoringClient> clients;

    // if logErrStream is true, need to explicitly call System.exit(0) at the end of the main thread.
    // device: e.g., "0,0,1,1,2,2,3,3" (indexes of gpu devices - using tensorflow)
    public MultiThreadedDeepScoringClient(boolean logErrStream, String device) {
        String[] devices = device.split(",");

        clients = new ArrayBlockingQueue<>(devices.length);

        ExecutorService service = Executors.newFixedThreadPool(devices.length);
        ArrayList<Future<DeepScoringClient>> futureClients = new ArrayList<>();
        for (String d : devices) {
            futureClients.add(service.submit(() -> new DeepScoringClient(logErrStream, Integer.parseInt(d))));
        }
        try {
            for (Future<DeepScoringClient> f : futureClients) {
                clients.add(f.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        service.shutdown();
    }

    @Override
    public ArrayList<Double> getScores(List<String> entitiesDesc, String quantityDesc) {
        if (entitiesDesc.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            DeepScoringClient scoringClient = clients.take();
            ArrayList<Double> results = scoringClient.getScores(entitiesDesc, quantityDesc);
            clients.put(scoringClient);
            return results;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double getScore(String typeDesc, String quantityDesc) {
        try {
            DeepScoringClient scoringClient = clients.take();
            double value = scoringClient.getScore(typeDesc, quantityDesc);
            clients.put(scoringClient);
            return value;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void benchmarking(String device) {
        MultiThreadedDeepScoringClient client = new MultiThreadedDeepScoringClient(true, device);
        System.out.print("Single/Multiple (S/M) > ");
        String line = new Scanner(System.in).nextLine();
        SelfMonitor m = new SelfMonitor("MultiThreadedDeepScoringClient_Performance", -1, 5);
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
        benchmarking("0,0,0,1,1,1");
//        DeepScoringClient client = new DeepScoringClient();
//        System.out.println(client.getScore("stadium in europe", "capacity"));
//        System.out.println(client.getScores(Arrays.asList("team", "stadium", "dog"), "capacity"));

        System.exit(0);
    }
}
