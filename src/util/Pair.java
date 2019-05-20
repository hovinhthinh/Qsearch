package util;

public class Pair<F, S> {
    public F first;
    public S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public Pair() {
        first = null;
        second = null;
    }

    @Override
    public String toString() {
        return first.toString() + "," + second.toString();
    }
}
