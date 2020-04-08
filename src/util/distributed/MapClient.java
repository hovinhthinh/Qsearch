package util.distributed;

import org.json.JSONArray;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

class MapClient {
    public static final int LONG_PROCESSING_INTERVAL = 300;
    public static final int NO_RESPONDING_INTERVAL = 30;

    private BufferedReader in;
    private PrintWriter out;
    private Process p;
    private PrintWriter outStream;
    private int clientId;
    private String mapClass, memorySpecs, errPath;

    private boolean isProcessing;
    private long lastMapStartTimestamp, lastResponseTimeStamp;

    private void startService(boolean isReset) {
        if (isReset) {
            System.out.println(String.format("__reset_client__ [Client#%d]", clientId));
        }
        destroy();
        try {
            String mainCmd =
                    String.format("./run_no_notification.sh %s util.distributed.MapInteractiveRunner %s 2%s%s",
                            memorySpecs,
                            mapClass,
                            isReset ? ">>" : ">",
                            errPath == null ? "/dev/null" : errPath
                    );
            String[] cmd = new String[]{
                    "/bin/sh", "-c",
                    mainCmd
            };
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File("./"));
            p = pb.start();
            in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)));

            String str;
            do {
                str = in.readLine();
                if (outStream != null) {
                    outStream.println(str);
                }
            } while (!str.startsWith(MapInteractiveRunner.ON_READY));
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    // String2StringMap
    public MapClient(int clientId, String String2StringMapClass, String memorySpecs, PrintWriter outStream, String errPath) {
        this.clientId = clientId;
        this.mapClass = String2StringMapClass;
        this.memorySpecs = memorySpecs;
        this.errPath = errPath;
        this.outStream = outStream;
        this.isProcessing = false;
        this.lastMapStartTimestamp = -1;
        this.lastResponseTimeStamp = -1;
        startService(false);
    }

    public int getId() {
        return clientId;
    }

    public List<String> map(String input) {
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
            if (outStream != null) {
                outStream.println(MapInteractiveRunner.ON_OUTPUT);
            }
            List<String> output = new LinkedList<>();
            JSONArray arr = new JSONArray(str.substring(str.indexOf("\t") + 1));
            for (int i = 0; i < arr.length(); ++i) {
                output.add(arr.getString(i));
            }
            return output;
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            System.err.println(String.format("__fatal_input__ [Client#%d]\t%s", clientId, input));
            startService(true);
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

    public void destroy() {
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
    }

    public void closeStreams() {
        try {
            outStream.close();
        } catch (Exception e) {
        }
    }
}