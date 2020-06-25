package util.distributed;

import util.Concurrent;
import util.FileUtils;
import util.Monitor;
import util.Pair;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class ClientMonitor extends Monitor {
    private AtomicInteger[] clientCount;
    private MapClient[] clients;

    public void incClient(int clientId) {
        clientCount[clientId].incrementAndGet();
    }

    @Override
    public int getCurrent() {
        int totalCount = 0;
        for (AtomicInteger c : clientCount) {
            totalCount += c.get();
        }
        return totalCount;
    }

    public ClientMonitor(String name, int total, int time, MapClient[] clients) {
        super(name, total, time);
        this.clients = clients;
        this.clientCount = new AtomicInteger[clients.length];
        for (int i = 0; i < clients.length; ++i) {
            clientCount[i] = new AtomicInteger();
        }
    }

    public String getReportString(int nKeyPerLine) {
        ArrayList<Pair<String, String>> kv = new ArrayList<>();
        for (int i = 0; i < clientCount.length; ++i) {
            kv.add(new Pair<>(String.format("Client#%d", i), String.format("%d", clientCount[i].get())));
        }
        int keyWidth = 0, valueWidth = 0;
        for (Pair<String, String> p : kv) {
            keyWidth = Math.max(keyWidth, p.first.length());
            valueWidth = Math.max(valueWidth, p.second.length());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Notes:    *Client is long processing    !Client is not responding");
        String formatStr = "  %s%s[%-" + keyWidth + "s: %-" + valueWidth + "s]";
        int nLine = (kv.size() - 1) / nKeyPerLine + 1;
        for (int i = 0; i < nLine; ++i) {
            sb.append("\r\n");
            for (int j = i; j < kv.size(); j += nLine) {
                Pair<String, String> p = kv.get(j);
                sb.append(String.format(formatStr,
                        clients[j].isNotResponding() ? "!" : " ",
                        clients[j].isLongProcessing() ? "*" : " ",
                        p.first,
                        p.second));
            }
        }
        return sb.toString();
    }

    @Override
    public void logProgress(Progress progress) {
        super.logProgress(progress);
        System.out.println(getReportString(6));
    }
}


class MultiThreadedMapClient {
    private ArrayBlockingQueue<MapClient> clients;

    public MapClient[] clientArray;

    public MultiThreadedMapClient(String String2StringMapClass, String memorySpecs, int nClients,
                                  String streamLogFolder, boolean streamOutput, boolean streamError) {
        clients = new ArrayBlockingQueue<>(nClients);

        ExecutorService service = Executors.newFixedThreadPool(nClients);
        try {
            ArrayList<Future<MapClient>> futureClients = new ArrayList<>();
            if (streamOutput || streamError) {
                new File(streamLogFolder).mkdirs();
            }
            for (int i = 0; i < nClients; ++i) {
                final int finalI = i;
                futureClients.add(service.submit(() -> {
                    PrintWriter outStream = streamOutput ? FileUtils.getPrintWriter(
                            String.format("%s/part%d.out", streamLogFolder, finalI), "UTF-8") : null;
                    String errPath = streamError ? String.format("%s/part%d.err", streamLogFolder, finalI) : null;
                    return new MapClient(finalI, String2StringMapClass, memorySpecs, outStream, errPath);
                }));
                Thread.sleep(250);
            }
            clientArray = new MapClient[nClients];
            for (int i = 0; i < nClients; ++i) {
                clients.add(clientArray[i] = futureClients.get(i).get());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            service.shutdown();
        }
    }

    public void clearIdleClients() {
        MapClient cli;
        while ((cli = clients.poll()) != null) {
            cli.destroy();
        }
    }

    // return map outputs & client id
    public Pair<List<String>, Integer> map(String input) {
        try {
            MapClient client = clients.take();
            List<String> result = client.map(input);
            clients.put(client);
            return new Pair<>(result, client.getId());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void closeStreams() {
        for (MapClient client : clients) {
            client.closeStreams();
        }
    }
}

public class ParallelMapClient {
    public static final boolean PART_OUTPUT_STREAM = false;
    public static final boolean PART_ERROR_STREAM = true;

    // args: <memorySpecsPerClient> <nClient> <String2StringMapClass> <inputFile> <outputFile>
    // TODO: support arguments in each single client.
    public static void main(String[] args) {
        int nClients = Integer.parseInt(args[1]);

        MultiThreadedMapClient client = new MultiThreadedMapClient(args[2], args[0], nClients,
                args[4] + ".log", PART_OUTPUT_STREAM, PART_ERROR_STREAM);

        FileUtils.LineStream in = FileUtils.getLineStream(args[3], "UTF-8");
        PrintWriter out = FileUtils.getPrintWriter(args[4], "UTF-8");

        ClientMonitor m = new ClientMonitor(ParallelMapClient.class.getName() + ":" + args[2], -1, 60, client.clientArray);
        m.start();

        new Thread(() -> {
            int nLine = 0;
            for (String line : FileUtils.getLineStream(args[3], "UTF-8")) {
                ++nLine;
            }
            m.setTotal(nLine);
        }).start();

        boolean mapResult = Concurrent.runAndWait(() -> {
            while (true) {
                String input;
                synchronized (in) {
                    input = in.readLine();
                }
                if (input == null) {
                    client.clearIdleClients();
                    return;
                }
                Pair<List<String>, Integer> output = client.map(input);
                synchronized (out) {
                    for (String o : output.first) {
                        out.println(o);
                    }
                }
                m.incClient(output.second);
            }
        }, nClients);
        if (!mapResult) {
            System.err.println("[FAIL] Some map client suddenly shutdowns.");
        }
        m.forceShutdown();
        out.close();
        client.closeStreams();
    }
}
