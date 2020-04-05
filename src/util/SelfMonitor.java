package util;

public class SelfMonitor extends Monitor {
    private int curr;
    private MetricReporter reporter;

    public int incAndGet() {
        return incAndGet(null);
    }

    public synchronized int incAndGet(String partName) {
        ++curr;
        if (partName != null) {
            reporter.recordCount(partName);
        }
        return curr;
    }

    @Override
    public int getCurrent() {
        return curr;
    }

    public SelfMonitor(String name, int total, int time) {
        super(name, total, time);
        curr = 0;
        reporter = new MetricReporter(name);
    }

    @Override
    public void logProgress(Progress progress) {
        super.logProgress(progress);
        if (!reporter.isEmpty()) {
            log(reporter.getReportString(4));
        }
    }
}
