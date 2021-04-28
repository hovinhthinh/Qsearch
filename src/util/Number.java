package util;

public class Number {

    public static double relativeNumericDistance(double a, double b) {
        double dist = Math.abs(a - b);
        if (dist <= Constants.EPS) {
            return 0;
        }
        return dist / Math.max(Math.abs(a), Math.abs(b));
    }
}
