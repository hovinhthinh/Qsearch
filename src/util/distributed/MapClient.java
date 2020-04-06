package util.distributed;

import org.json.JSONArray;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

class MapClient {
    private BufferedReader in;
    private PrintWriter out;
    private Process p;
    private PrintWriter outStream;
    private String clientName, mapClass, memorySpecs, errPath;

    private void startService(boolean isReset) {
        if (isReset) {
            System.out.println(String.format("__reset_client__ [%s]", clientName));
        }
        try {
            p.destroy();
        } catch (Exception e) {
        }
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
    public MapClient(String clientName, String String2StringMapClass, String memorySpecs, PrintWriter outStream, String errPath) {
        this.clientName = clientName;
        this.mapClass = String2StringMapClass;
        this.memorySpecs = memorySpecs;
        this.errPath = errPath;
        this.outStream = outStream;
        startService(false);
    }

    public String getName() {
        return clientName;
    }

    public List<String> map(String input) {
        try {
            out.println(input);
            out.flush();
            String str;
            while (!(str = in.readLine()).startsWith(MapInteractiveRunner.ON_OUTPUT)) {
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
            System.err.println(String.format("__fatal_input__ [%s]\t%s", clientName, input));
            startService(true);
            return new LinkedList<>();
        }
    }

    @Override
    protected void finalize() throws Throwable {
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
        super.finalize();
    }

    public void closeStreams() {
        try {
            outStream.close();
        } catch (Exception e) {
        }
    }
}