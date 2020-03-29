package util.distributed;

import util.Concurrent;
import util.FileUtils;
import util.SelfMonitor;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;


class MultiThreadedMapClient {

    private ArrayBlockingQueue<MapClient> clients;

    public MultiThreadedMapClient(String String2StringMapClass, String memorySpecs, int nClients, String outStreamPrefix, String errStreamPrefix) {
        clients = new ArrayBlockingQueue<>(nClients);

        ExecutorService service = Executors.newFixedThreadPool(nClients);
        ArrayList<Future<MapClient>> futureClients = new ArrayList<>();
        for (int i = 0; i < nClients; ++i) {
            final int finalI = i;
            futureClients.add(service.submit(() -> {
                PrintWriter outStream = outStreamPrefix == null ? null : FileUtils.getPrintWriter(
                        String.format("%s.part%03d.out", outStreamPrefix, finalI), "UTF-8");
                PrintWriter errStream = errStreamPrefix == null ? null : FileUtils.getPrintWriter(
                        String.format("%s.part%03d.err", errStreamPrefix, finalI), "UTF-8");
                return new MapClient(String2StringMapClass, memorySpecs, outStream, errStream);
            }));
        }
        try {
            for (Future<MapClient> f : futureClients) {
                clients.add(f.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            service.shutdown();
        }
    }

    public List<String> map(String input) {
        try {
            MapClient client = clients.take();
            List<String> result = client.map(input);
            clients.put(client);
            return result;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void closeOutAndErrStreams() {
        for (MapClient client : clients) {
            client.closeOutAndErrStreams();
        }
    }
}


public class ParallelMapClient {
    public static final boolean PART_OUTPUT_STREAM = false;
    public static final boolean PART_ERROR_STREAM = true;

    // args: <memorySpecsPerClient> <nClient> <String2StringMapClass> <inputFile> <outputFile>
    // TODO: support arguments in each single client.
    public static void main(String[] args) throws Exception {
        int nClients = Integer.parseInt(args[1]);

        MultiThreadedMapClient client = new MultiThreadedMapClient(args[2], args[0], nClients,
                PART_OUTPUT_STREAM ? args[4] : null, PART_ERROR_STREAM ? args[4] : null);

        FileUtils.LineStream in = FileUtils.getLineStream(args[3], "UTF-8");
        PrintWriter out = new PrintWriter(args[4], "UTF-8");

        SelfMonitor m = new SelfMonitor(ParallelMapClient.class.getName() + ":" + args[2], -1, 60);
        m.start();
        Concurrent.runAndWait(() -> {
            while (true) {
                String input;
                synchronized (in) {
                    input = in.readLine();
                }
                if (input == null) {
                    return;
                }
                List<String> output = client.map(input);
                synchronized (out) {
                    for (String o : output) {
                        out.println(o);
                    }
                }
                m.incAndGet();
            }
        }, nClients);
        m.forceShutdown();
        out.close();
        client.closeOutAndErrStreams();
        System.exit(0);
    }
}
