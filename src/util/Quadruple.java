package util;


public class Quadruple<A, B, C, D> {
    public A first;
    public B second;
    public C third;
    public D fourth;

    public Quadruple(A first, B second, C third, D fourth) {
        this.first = first;
        this.second = second;
        this.third = third;
        this.fourth = fourth;
    }

    public Quadruple() {
        first = null;
        second = null;
        third = null;
        fourth = null;
    }

    @Override
    public String toString() {
        return "(" + first.toString() + "," + second.toString() + "," + third.toString() + "," + fourth.toString() + ")";
    }
}