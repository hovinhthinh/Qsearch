package util;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

// Usage: Override getCurrent() method, then call start(), and call forceShutdown() if necessary.
public abstract class Monitor extends Thread {
    private String name;
    private int total, time;
    private PrintWriter out;
    private boolean stopped;
    private int current, lastTick;

    // Start from 0, end at <total>. <time> in seconds is the delay between 2 logs.
    // <total> == -1 indicates INF. In this case, we need to call forceShutdown() manually.
    public Monitor(String name, int total, int time, OutputStream out) {
        if (total < 0 && total != -1) {
            throw new RuntimeException("'total' is invalid.");
        }
        this.name = name;
        this.total = total;
        this.time = time;
        if (out == null) {
            this.out = null;
        } else {
            this.out = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        }
        this.stopped = false;

        this.current = 0;
        this.lastTick = 0;
    }

    public Monitor(String name, int total, int time) {
        this(name, total, time, null);
    }

    public abstract int getCurrent();

    public final void log(String logString) {
        if (out == null) {
            System.out.println(logString);
        } else {
            out.println(logString);
            out.flush();
        }
    }

    public void setTotal(int total) {
        this.total = total;
    }

    // Override these log methods for another logging way.
    public void logStart() {
        log(String.format("MONITOR [%s] : STARTED.", name));
    }

    public void logDone() {
        log(String.format("MONITOR [%s] : DONE.", name));
    }

    public void logForcedShutdown() {
        log(String.format("MONITOR [%s] : FORCED SHUTDOWN.", name));
    }

    public void logProgress(Progress progress) {
        String currentString = progress.total == -1
                ? String.format("%d/--", progress.current)
                : String.format("%d/%d", progress.current, progress.total);
        String percentString = progress.total == -1 ? "--" : String.format("%.2f%%", progress.percent);
        String speedString = String.format("%.2f/sec", progress.speed);
        String etaString = progress.total == -1 ? "--d --:--:--" :
                String.format("%dd %02d:%02d:%02d", progress.eta_d, progress.eta_h, progress.eta_m, progress.eta_s);

        log(String.format("MONITOR [%s] : current: %s     percent: %s     speed: %s     eta: %s",
                name, currentString, percentString, speedString, etaString));
    }

    public void run() {
        logStart();
        if (total == 0) {
            stopped = true;
            logDone();
            return;
        }
        while (!stopped) {
            try {
                Thread.sleep(time * 1000);
            } catch (InterruptedException e) {
                break;
            }
            current = getCurrent();
            if (current < 0 || (total >= 0 && current > total)) {
                throw new RuntimeException("'getCurrent()' is invalid.");
            }

            // Compute progress.
            Progress progress = new Progress();
            progress.current = current;
            if (total != -1) {
                progress.total = total;
            }
            if (total != -1) {
                progress.percent = ((double) current) / total * 100;
            }
            progress.speed = ((double) current - lastTick) / time;
            if (Math.abs(progress.speed) >= 1e-2) {
                int remainingTimeInSeconds = (int) ((total - current) / progress.speed);
                progress.eta_d = remainingTimeInSeconds / (24 * 3600);
                progress.eta_h = (remainingTimeInSeconds % (24 * 3600)) / 3600;
                progress.eta_m = (remainingTimeInSeconds % 3600) / 60;
                progress.eta_s = remainingTimeInSeconds % 60;
            }
            logProgress(progress);

            lastTick = current;

            if (current == total) {
                stopped = true;
            }
        }
        current = getCurrent();
        if (current < 0 || (total >= 0 && current > total)) {
            throw new RuntimeException("'getCurrent()' is invalid.");
        }
        if (current == total) {
            logDone();
        } else {
            logForcedShutdown();
        }
    }

    public boolean forceShutdown() {
        if (!stopped) {
            stopped = true;
            this.interrupt();
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (out != null) {
            out.close();
        }
        super.finalize();
    }

    public static final class Progress {
        int current = 0;
        int total = -1;
        double percent = -1; // -1 means unknown.
        double speed = 0;
        int eta_d = -1, eta_h = -1, eta_m = -1, eta_s = -1; // -1 means unknown.
    }
}
