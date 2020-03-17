package util.distributed;

import org.json.JSONArray;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

class MapClient {
    private BufferedReader in = null, err = null;
    private PrintWriter out = null;
    private Process p = null;
    private PrintWriter outStream = null, errStream = null;

    // String2StringMap
    public MapClient(String String2StringMapClass, String memorySpecs, PrintWriter outStream, PrintWriter errStream) {
        this.outStream = outStream;
        this.errStream = errStream;
        try {
            String mainCmd = String.format("./run_no_notification.sh %s util.distributed.MapInteractiveRunner %s", memorySpecs, String2StringMapClass);
            String[] cmd = new String[]{
                    "/bin/sh", "-c",
                    mainCmd
            };
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
                            errStream.println(str);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

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

    public List<String> map(String input) {
        out.println(input);
        out.flush();
        try {
            String str;
            while (!(str = in.readLine()).startsWith(MapInteractiveRunner.ON_OUTPUT)) {
                if (outStream != null) {
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
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            in.close();
        } catch (Exception e) {
        }
        try {
            err.close();
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

    public void closeOutAndErrStreams() {
        try {
            outStream.close();
        } catch (Exception e) {
        }
        try {
            errStream.close();
        } catch (Exception e) {
        }
    }
}