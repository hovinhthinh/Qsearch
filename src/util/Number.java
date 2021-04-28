package util;

public class Number {

    public static double relativeNumericDistance(double a, double b) {
        if (a == 0 && b == 0) {
            return 0;
        }
        return Math.abs(a - b) / Math.max(Math.abs(a), Math.abs(b));
    }
}
