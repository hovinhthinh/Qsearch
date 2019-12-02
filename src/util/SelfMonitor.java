package util;

import java.util.concurrent.atomic.AtomicInteger;

public class SelfMonitor extends Monitor {
    private AtomicInteger curr = new AtomicInteger(0);

    public int incAndGet() {
        return curr.incrementAndGet();
    }

    @Override
    public int getCurrent() {
        return curr.get();
    }

    public SelfMonitor(String name, int total, int time) {
        super(name, total, time);
    }
}
