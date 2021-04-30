package util;

public class Vectors {
    public static double max(double[] v) {
        if (v.length == 0) {
            return 0;
        }
        double m = v[0];
        for (int i = 1; i < v.length; ++i) {
            m = Math.max(m, v[i]);
        }
        return m;
    }

    public static double min(double[] v) {
        if (v.length == 0) {
            return 0;
        }
        double m = v[0];
        for (int i = 1; i < v.length; ++i) {
            m = Math.min(m, v[i]);
        }
        return m;
    }

    public static double[] multiply(double[] v, double scala) {
        double[] r = new double[v.length];
        for (int i = 0; i < v.length; ++i) {
            r[i] = v[i] * scala;
        }
        return r;
    }

    public static double[] sum(double[] a, double[] b) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; ++i) {
            r[i] = a[i] + b[i];
        }
        return r;
    }

    public static double dot(double[] a, double[] b) {
        double r = 0;
        for (int i = 0; i < a.length; ++i) {
            r += a[i] * b[i];
        }
        return r;
    }

    // Normally cosine gives a value from -1 to 1. However, we normalize this value to 0 -> 1, lower is better
    public static double cosineD(double[] a, double[] b) {
        double dotProduct = 0;
        double aLength = 0, bLength = 0;
        for (int i = 0; i < a.length; ++i) {
            dotProduct += a[i] * b[i];
            aLength += a[i] * a[i];
            bLength += b[i] * b[i];
        }

        double cosine = dotProduct / Math.sqrt(aLength) / Math.sqrt(bLength);

        // Normalize.
        return 0.5 - cosine / 2;
    }

    // equals to normD with L = 2
    public static double euclideanD(double[] a, double[] b) {
        double d = 0;
        for (int i = 0; i < a.length; ++i) {
            d += (a[i] - b[i]) * (a[i] - b[i]);
        }

        return Math.sqrt(d);
    }

    public static double normD(double[] a, double[] b, double L) {
        double d = 0;
        for (int i = 0; i < a.length; ++i) {
            d += Math.pow(Math.abs(a[i] - b[i]), L);
        }
        return Math.pow(d, 1 / L);
    }
}
