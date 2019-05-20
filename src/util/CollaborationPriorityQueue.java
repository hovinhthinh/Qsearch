package util;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

// Blocking priority queue, returns null for pop operations only when all collaborators call "pop", otherwise they
// will be blocked until there is some element pushed into the queue.
class CollaborationPriorityQueue<T> {
    private final Object lock = new Object();
    private Queue<T> queue;
    private int numberOfCollaborators;
    private int currentWaitCount = 0;

    private boolean isEnded = false;

    // comparator == null denotes a normal FIFO queue.
    public CollaborationPriorityQueue(int nCollaborators, Comparator<T> comparator) {
        if (nCollaborators < 1) {
            throw new RuntimeException("nCollaborators must be positive.");
        }
        numberOfCollaborators = nCollaborators;
        if (comparator == null) {
            queue = new LinkedList<>();
        } else {
            queue = new PriorityQueue<>(comparator);
        }
    }

    public CollaborationPriorityQueue(int nCollaborators) {
        this(nCollaborators, null);
    }

    public boolean push(T element) {
        synchronized (lock) {
            boolean result = queue.add(element);
            lock.notify();
            return result;
        }
    }

    public T pop() {
        synchronized (lock) {
            try {
                while (true) {
                    if (isEnded) {
                        return null;
                    }
                    if (!queue.isEmpty()) {
                        return queue.poll();
                    }
                    ++currentWaitCount;
                    if (currentWaitCount == numberOfCollaborators) {
                        isEnded = true;
                        lock.notify();
                        return null;
                    }
                    lock.wait();
                    --currentWaitCount;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public int size() {
        return queue.size();
    }

    // Flag to check if the collaboration is done.
    public boolean isEnded() {
        return this.isEnded;
    }
}