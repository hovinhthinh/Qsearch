package qkbc.distribution;

import umontreal.ssj.probdist.ContinuousDistribution;
import util.Pair;

import java.util.Arrays;
import java.util.Comparator;

public class IntegralDistributionApproximator {
    public static final int INTEGRAL_N_BINS = 1 << 16;
    public static final double INVERSE_F_IGNORE_THRESHOLD = 1e-4;

    private double[] binX, binDensity;
    private double binWidth;

    private double[] cdf, pValue;

    public IntegralDistributionApproximator(ContinuousDistribution d) {
        binX = new double[INTEGRAL_N_BINS];
        binDensity = new double[INTEGRAL_N_BINS];
        double start = d.inverseF(INVERSE_F_IGNORE_THRESHOLD), end = d.inverseF(1 - INVERSE_F_IGNORE_THRESHOLD);

        binWidth = (end - start) / INTEGRAL_N_BINS;

        // Init density
        for (int i = 0; i < INTEGRAL_N_BINS; ++i) {
            binX[i] = start + binWidth * (i + 0.5);
            binDensity[i] = d.density(binX[i]);
        }

        // Init cdf
        cdf = new double[INTEGRAL_N_BINS];

        for (int i = 0; i < INTEGRAL_N_BINS; ++i) {
            cdf[i] = binDensity[i];
            if (i > 0) {
                cdf[i] += cdf[i - 1];
            }
        }
        for (int i = 0; i < INTEGRAL_N_BINS; ++i) {
            cdf[i] *= binWidth;
        }

        // init pValue
        pValue = new double[INTEGRAL_N_BINS];

        Pair<Integer, Double>[] pos = new Pair[INTEGRAL_N_BINS];
        for (int i = 0; i < INTEGRAL_N_BINS; ++i) {
            pos[i] = new Pair<>(i, binDensity[i]);
        }
        Arrays.sort(pos, Comparator.comparing(o -> o.second));
        for (int i = 0; i < INTEGRAL_N_BINS; ++i) {
            pValue[pos[i].first] = binDensity[pos[i].first];
            if (i > 0) {
                pValue[pos[i].first] += pValue[pos[i - 1].first];
            }
        }
        for (int i = 0; i < INTEGRAL_N_BINS; ++i) {
            pValue[i] *= binWidth;
        }
    }

    private int getBin(double v) {
        int l = -1, r = INTEGRAL_N_BINS;
        while (l + 1 < r) {
            int mid = (l + r) >> 1;
            if (binX[mid] <= v) {
                l = mid;
            } else {
                r = mid;
            }
        }
        if (l == -1) {
            return v >= binX[0] - binWidth * 0.5 ? 0 : -1;
        }

        return (v <= binX[l] + binWidth * 0.5) ? l : l + 1;
    }

    public double getEstimatedCdf(double v) {
        int bin = getBin(v);
        if (bin == -1) {
            return 0;
        }
        if (bin == INTEGRAL_N_BINS) {
            return 1;
        }
        return cdf[bin];
    }

    public double getEstimatedPValue(double v) {
        int bin = getBin(v);
        if (bin == -1 || bin == INTEGRAL_N_BINS) {
            return 0;
        }
        return pValue[bin];
    }
}
