package storage;

public abstract class StreamedIterable<T> implements Iterable<T> {
    public boolean error = false;
    public int total = -1;
    public int streamed = 0;
}