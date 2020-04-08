package util.distributed;

import util.Concurrent;
import util.FileUtils;
import util.Monitor;
import util.Pair;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

class ClientMonitor extends Monitor {
    private int[] clientCount;
    private MapClient[] clients;

    public void incClient(int clientId) {
        ++clientCount[clientId];
    }

    @Override
    public int getCurrent() {
        int totalCount = 0;
        for (int c : clientCount) {
            totalCount += c;
        }
        return totalCount;
    }

    public ClientMonitor(String name, int total, int time, MapClient[] clients) {
        super(name, total, time);
        this.clients = clients;
        this.clientCount = new int[clients.length];
    }

    public String getReportString(int nKeyPerLine) {
        ArrayList<Pair<String, String>> kv = new ArrayList<>();
        for (int i = 0; i < clientCount.length; ++i) {
            kv.add(new Pair<>(String.format("Client#%d", i), String.format("%d", clientCount[i])));
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
                        clients[j].isNotAlive() ? "!" : " ",
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

    public MultiThreadedMapClient(String String2StringMapClass, String memorySpecs, int nClients, String outStreamPrefix, String errStreamPrefix) {
        clients = new ArrayBlockingQueue<>(nClients);

        ExecutorService service = Executors.newFixedThreadPool(nClients);
        ArrayList<Future<MapClient>> futureClients = new ArrayList<>();
        for (int i = 0; i < nClients; ++i) {
            final int finalI = i;
            futureClients.add(service.submit(() -> {
                PrintWriter outStream = outStreamPrefix == null ? null : FileUtils.getPrintWriter(
                        String.format("%s.part%03d.out", outStreamPrefix, finalI), "UTF-8");
                String errPath = errStreamPrefix == null ? null : String.format("%s.part%03d.err", errStreamPrefix, finalI);
                return new MapClient(finalI, String2StringMapClass, memorySpecs, outStream, errPath);
            }));
        }
        clientArray = new MapClient[nClients];
        try {
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
                PART_OUTPUT_STREAM ? args[4] : null, PART_ERROR_STREAM ? args[4] : null);

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
