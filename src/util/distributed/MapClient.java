package util.distributed;

import org.json.JSONArray;
import util.FileUtils;
import util.SelfMonitor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

// call MapInteractiveRunner
class MapClient {
    public static final int LONG_PROCESSING_INTERVAL = 300;
    public static final int NO_RESPONDING_INTERVAL = 30;

    private BufferedReader in, err;
    private PrintWriter out;
    private Process p;
    private PrintWriter outStream, errStream;
    private int clientId;
    private String mapClass, memorySpecs;

    private boolean isProcessing;
    private long lastMapStartTimestamp, lastResponseTimeStamp;

    private void startService() {
        destroyInteractiveClient();
        try {
            String mainCmd = String.format("./run_no_notification.sh %s util.distributed.MapInteractiveRunner %s",
                    memorySpecs, mapClass);
            String[] cmd = new String[]{"/bin/sh", "-c", mainCmd};
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File("./"));
            p = pb.start();
            in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)));
            err = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));

            new Thread(() -> {
                try {
                    String str;
                    while ((str = err.readLine()) != null) {
                        if (errStream != null) {
                            synchronized (errStream) {
                                errStream.println(str);
                            }
                        }
                    }
                } catch (IOException e) {
                }
            }).start();

            String str;
            do {
                str = in.readLine();
                if (str != null && outStream != null) {
                    outStream.println(str);
                }
            } while (!str.startsWith(MapInteractiveRunner.ON_READY));
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            if (outStream != null) {
                outStream.flush();
            }
            if (errStream != null) {
                synchronized (errStream) {
                    errStream.flush();
                }
            }
            throw new RuntimeException(e);
        }
    }

    public MapClient(int clientId, String String2StringMapClass, String memorySpecs, String outPath, String errPath) {
        this.clientId = clientId;
        this.mapClass = String2StringMapClass;
        this.memorySpecs = memorySpecs;
        this.outStream = outPath == null ? null : FileUtils.getPrintWriter(outPath, "UTF-8");
        this.errStream = errPath == null ? null : FileUtils.getPrintWriter(errPath, "UTF-8");
        this.isProcessing = false;
        this.lastMapStartTimestamp = -1;
        this.lastResponseTimeStamp = -1;
        startService();
    }

    public int getId() {
        return clientId;
    }

    public synchronized List<String> map(String input) {
        try {
            isProcessing = true;
            lastMapStartTimestamp = lastResponseTimeStamp = System.currentTimeMillis();
            out.println(input);
            out.flush();
            String str;
            while (!(str = in.readLine()).startsWith(MapInteractiveRunner.ON_OUTPUT)) {
                lastResponseTimeStamp = System.currentTimeMillis();
                if (outStream != null && !str.equals(MapInteractiveRunner.ON_KEEP_ALIVE)) {
                    outStream.println(str);
                }
            }
            List<String> output = new LinkedList<>();
            JSONArray arr = new JSONArray(str.substring(str.indexOf("\t") + 1));
            for (int i = 0; i < arr.length(); ++i) {
                output.add(arr.getString(i));
            }
            return output;
        } catch (IOException | NullPointerException e) {
            if (errStream != null) {
                synchronized (errStream) {
                    errStream.println(String.format("__fatal_input__ [Client#%d]\t%s", clientId, input));
                }
            }
            startService();
            return new LinkedList<>();
        } finally {
            isProcessing = false;
        }
    }

    public boolean isLongProcessing() {
        return isProcessing && System.currentTimeMillis() - lastMapStartTimestamp >= LONG_PROCESSING_INTERVAL * 1000;
    }

    public boolean isNotResponding() {
        return isProcessing && System.currentTimeMillis() - lastResponseTimeStamp >= (MapInteractiveRunner.KEEP_ALIVE_INTERVAL + NO_RESPONDING_INTERVAL) * 1000;
    }

    public void destroyInteractiveClient() {
        try {
            in.close();
        } catch (Exception e) {
        }
        try {
            out.close();
        } catch (Exception e) {
        }
        try {
            err.close();
        } catch (Exception e) {
        }
        try {
            p.destroy();
        } catch (Exception e) {
        }
    }

    public void closeStreams() {
        try {
            outStream.close();
        } catch (Exception e) {
        }
        try {
            errStream.close();
        } catch (Exception e) {
        }
    }

    // args: <memorySpecs> <String2StringMapClass> <inputFile> <outputFile> [stdout] [stderr]
    // <stdout> and <stderr> are required to redirect output from MapInteractiveRunner to a file.
    public static void main(String[] args) {
        MapClient mapper = new MapClient(-1, args[1], args[0],
                args.length > 4 ? args[4] : null, args.length > 5 ? args[5] : null);

        PrintWriter out = FileUtils.getPrintWriter(args[3], "UTF-8");
        SelfMonitor m = new SelfMonitor(args[1], -1, 60);
        m.start();
        for (String line : FileUtils.getLineStream(args[2], "UTF-8")) {
            List<String> result = mapper.map(line);
            for (String r : result) {
                out.println(r);
            }
            m.incAndGet();
        }
        m.forceShutdown();
        out.close();

        mapper.destroyInteractiveClient();
        mapper.closeStreams();
    }
}