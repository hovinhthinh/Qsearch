package util;

public class Number {

    public static double relativeNumericDistance(double a, double b) {
        if (Math.abs(a - b) <= Constants.EPS) {
            return 0;
        }
        return Math.abs(a - b) / Math.max(Math.abs(a), Math.abs(b));
    }
}
