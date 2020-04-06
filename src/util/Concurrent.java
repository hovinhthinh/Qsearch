package util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Concurrent {
    public static boolean runAndWait(Runnable run, int nThreads) {
        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < nThreads; ++i) {
            futures.add(service.submit(run));
        }
        boolean result = true;
        for (Future f : futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                result = false;
            }
        }
        service.shutdown();
        return result;
    }

    public static class BoundedExecutor {
        private final ExecutorService exec;
        private final Semaphore semaphore;

        public BoundedExecutor(int nThreads) {
            exec = Executors.newFixedThreadPool(nThreads);
            semaphore = new Semaphore(nThreads);
        }

        // Block here when the number of running tasks is nThreads.
        public Future submit(final Callable task) throws InterruptedException {
            semaphore.acquire();
            try {
                return exec.submit(() -> {
                    try {
                        return task.call();
                    } finally {
                        semaphore.release();
                    }
                });
            } catch (RejectedExecutionException e) {
                semaphore.release();
                throw e;
            }
        }

        // Block here when the number of running tasks is nThreads.
        public Future submit(final Runnable task) throws InterruptedException {
            semaphore.acquire();
            try {
                return exec.submit(() -> {
                    try {
                        task.run();
                        return null;
                    } finally {
                        semaphore.release();
                    }
                });
            } catch (RejectedExecutionException e) {
                semaphore.release();
                throw e;
            }
        }

        public void joinAndShutdown(int second) throws InterruptedException {
            exec.shutdown();
            exec.awaitTermination(second, TimeUnit.SECONDS);
        }
    }
}
