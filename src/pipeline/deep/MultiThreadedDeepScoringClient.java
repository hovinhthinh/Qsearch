package pipeline.deep;

import java.util.ArrayList;
import java.util.List;
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
}
