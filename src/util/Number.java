package util;

public class Number {

    public static double relativeNumericDistance(double a, double b) {
        double dist = Math.abs(a - b);
        if (dist <= Constants.EPS) {
            return 0;
        }
        return dist / Math.max(Math.abs(a), Math.abs(b));
    }

    public static String getWrittenString(double n, boolean morePrecise) {
        if (Math.abs(n) >= 1e9) {
            return String.format(morePrecise ? "%.2f" : "%.1f", n / 1e9) + " billion";
        } else if (n >= 1e6) {
            return String.format(morePrecise ? "%.2f" : "%.1f", n / 1e6) + " million";
        } else if (n >= 1e5) {
            return String.format(morePrecise ? "%.1f" : "%.0f", n / 1e3) + " thousand";
        } else {
            return String.format(morePrecise ? "%.3f" : "%.2f", n);
        }
    }
}
