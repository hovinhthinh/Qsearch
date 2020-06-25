package util.distributed;

import org.json.JSONArray;
import util.FileUtils;
import util.SelfMonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// Also support SLURM.
public class MapInteractiveRunner {
    public static final String ON_READY = "__map_ready__";
    public static final String ON_OUTPUT = "__interactive_output__";
    public static final String ON_FAIL = "__fail_input__";
    public static final String ON_KEEP_ALIVE = "__im_alive__";

    public static final int KEEP_ALIVE_INTERVAL = 10;
    public static final int GC_INTERVAL = 1000;
    public static final int SELF_KILLING_LONG_PROCESSING_TIMEOUT = 3600;

    // args: <String2StringMapClass>
    // for interactive mode
    //
    // or: <String2StringMapClass> <inputFile> <outputFile>
    public static void main(String[] args) {
        String2StringMap mapper;
        try {
            mapper = (String2StringMap) Class.forName(args[0]).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        mapper.before();
        System.out.println();
        System.out.println(ON_READY);
        System.out.flush();

        System.gc();

        // if input and output file are given, turns to normal mode.
        if (args.length == 3) {
            PrintWriter out = FileUtils.getPrintWriter(args[2], "UTF-8");
            SelfMonitor m = new SelfMonitor(args[0], -1, 60);
            m.start();
            for (String line : FileUtils.getLineStream(args[1], "UTF-8")) {
                try {
                    List<String> result = mapper.map(line);
                    if (result != null) {
                        for (String r : result) {
                            out.println(r);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println(String.format("%s\t%s", ON_FAIL, line));
                }
                m.incAndGet();
            }
            m.forceShutdown();
            out.close();
            return;
        }

        final AtomicBoolean isProcessingAnInput = new AtomicBoolean(false);
        final AtomicLong lastInputTimestamp = new AtomicLong(-1);

        Thread keepAlive = new Thread(() -> {
            do {
                try {
                    Thread.sleep(KEEP_ALIVE_INTERVAL * 1000);
                } catch (InterruptedException e) {
                    break;
                }
                if (isProcessingAnInput.get() && System.currentTimeMillis() >= lastInputTimestamp.get() + SELF_KILLING_LONG_PROCESSING_TIMEOUT * 1000) {
                    Runtime.getRuntime().halt(1);
                }
                synchronized (System.out) {
                    System.out.println(ON_KEEP_ALIVE);
                    System.out.flush();
                }
            } while (!Thread.currentThread().isInterrupted());
        });
        keepAlive.start();

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

            String str;

            long lastGCTime = System.currentTimeMillis();
            while ((str = in.readLine()) != null) {
                lastInputTimestamp.set(System.currentTimeMillis());
                isProcessingAnInput.set(true);
                List<String> output = null;
                try {
                    output = mapper.map(str);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println(String.format("%s\t%s", ON_FAIL, str));
                }
                if (System.currentTimeMillis() >= lastGCTime + GC_INTERVAL * 1000) {
                    System.gc();
                    lastGCTime = System.currentTimeMillis();
                }

                JSONArray arr = new JSONArray();
                if (output != null) {
                    for (String o : output) {
                        arr.put(o);
                    }
                }
                synchronized (System.out) {
                    System.out.println();
                    System.out.println(String.format("%s\t%s", ON_OUTPUT, arr.toString()));
                    System.out.flush();
                }
                isProcessingAnInput.set(false);
            }
            mapper.after();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            keepAlive.interrupt();
        }
    }
}
