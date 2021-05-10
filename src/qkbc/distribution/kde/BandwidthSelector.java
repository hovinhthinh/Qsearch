package qkbc.distribution.kde;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.exception.NoBracketingException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import umontreal.ssj.probdist.EmpiricalDist;
import util.Pair;
import util.Vectors;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public interface BandwidthSelector {
    double compute(double[] samples);

    // Preferred for samples generated from normal dist, with normal dist kernel
    class Scott implements BandwidthSelector {
        private static double CONST = Math.pow(4.0 / 3, 1.0 / 5);

        @Override
        public double compute(double[] samples) {
            if (samples.length == 0) {
                throw new RuntimeException("Sample size cannot be 0");
            }
            if (samples.length == 1) {
                return 1;
            }
            return CONST * (new EmpiricalDist(samples).getSampleStandardDeviation()) * Math.pow(samples.length, -1.0 / 5);
        }
    }

    // Preferred for samples generated from normal dist, with normal dist kernel (but more robust than Scott's rule of thumb)
    class Silverman implements BandwidthSelector {

        @Override
        public double compute(double[] samples) {
            if (samples.length == 0) {
                throw new RuntimeException("Sample size cannot be 0");
            }
            if (samples.length == 1) {
                return 1;
            }
            DescriptiveStatistics ds = new DescriptiveStatistics(samples);
            double iqr = ds.getPercentile(75) - ds.getPercentile(25);

            return 0.9 * Math.min(new EmpiricalDist(samples).getSampleStandardDeviation(), iqr / 1.3489795003921634)
                    * Math.pow(samples.length, -1.0 / 5);
        }
    }

    // Improved Sheather Jones (ISJ) algorithm from the paper by Botev et al.
    // Following https://github.com/tommyod/KDEpy
    // Used for normal dist kernel
    class ISJ implements BandwidthSelector {
        static int N_POINTS = 1 << 10;
        static double GRID_BOUNDARY_ABS = 6, GRID_BOUNDARY_REL = 0.5;

        private static double[] autogrid(double[] values) {
            double max = Vectors.max(values), min = Vectors.min(values);
            double range = max - min;

            double border = Math.max(GRID_BOUNDARY_REL * range, GRID_BOUNDARY_ABS);
            double start = min - border;
            double step = (range + border * 2) / (N_POINTS - 1);

            double[] grid = new double[N_POINTS];
            for (int i = 0; i < N_POINTS; ++i) {
                grid[i] = start + i * step;
            }
            return grid;
        }

        private static double[] linearBinning(double[] data, double[] grid) {
            double max = Vectors.max(grid), min = Vectors.min(grid);
            int nIntervals = grid.length - 1;
            double dx = (max - min) / nIntervals;

            double[] transformedData = new double[data.length];
            Pair<Integer, Double>[] intFrac = new Pair[data.length];
            for (int i = 0; i < data.length; ++i) {
                transformedData[i] = (data[i] - min) / dx;
                int integral = (int) transformedData[i];
                double fractional = transformedData[i] - integral;
                intFrac[i] = new Pair<>(integral, fractional);
            }
            Arrays.sort(intFrac, Comparator.comparing(a -> a.first));

            double[] fracWeights = new double[data.length], negFracWeights = new double[data.length];
            for (int i = 0; i < data.length; ++i) {
                fracWeights[i] = intFrac[i].second / data.length;
                negFracWeights[i] = 1.0 / data.length - fracWeights[i];
            }

            double[] w = new double[grid.length];
            for (int i = 0; i < data.length; ++i) {
                if (intFrac[i].first < 0 || intFrac[i].first > grid.length) {
                    continue;
                }
                int j = i;
                while (j < data.length && intFrac[j].first == intFrac[i].first) {
                    w[intFrac[i].first] += negFracWeights[j];
                    if (intFrac[i].first + 1 < w.length) {
                        w[intFrac[i].first + 1] += fracWeights[j];
                    }
                    ++j;
                }
                i = j - 1;
            }
            return w;
        }


        public static double[] dct1DTransform(double[] array) {
            int m = array.length;
            double[] dct = new double[m];

            for (int i = 0; i < m; i++) {

                double sum = 0;
                for (int k = 0; k < m; k++) {
                    sum += array[k] * Math.cos((2 * k + 1) * i * Math.PI / (2 * m));
                }
                dct[i] = 2 * sum;
//                double ci = (i == 0) ? (1 / Math.sqrt(4 * m)) : (Math.sqrt(1) / Math.sqrt(2 * m));
//                dct[i] *= ci;
            }
            return dct;
        }

        private static double findRoot(UnivariateFunction f, int N) {
            BrentSolver solver = new BrentSolver(8.881784197001252e-16, 2e-12);

            N = Math.max(Math.min(1050, N), 50);
            double tol = 1e-12 + 0.01 * (N - 50) / 1000;

            do {
                double x;
                try {
                    x = solver.solve(100, f, 0, tol);
                } catch (TooManyEvaluationsException | NoBracketingException e) {
                    x = 0;
                }
                if (x > 0) {
                    return x;
                }
                if (tol >= 1) {
                    throw new RuntimeException("Cannot find a positive solution within 0-1");
                }
                tol *= 2;
            } while (true);
        }


        @Override
        public double compute(double[] samples) {
            double range = Vectors.max(samples) - Vectors.min(samples);

            double[] initialData = linearBinning(samples, autogrid(samples));
            double[] a = dct1DTransform(initialData);

            double[] I_sq = new double[N_POINTS - 1], a2 = new double[N_POINTS - 1];
            for (int i = 1; i < N_POINTS; ++i) {
                I_sq[i - 1] = Math.pow(i, 2);
                a2[i - 1] = a[i] * a[i] / 4;
            }

            int N = new HashSet(DoubleStream.of(samples).boxed().collect(Collectors.toList())).size();

            double tStar = findRoot((t) -> {
                int ell = 7;
                double z = 0;
                for (int i = 0; i < I_sq.length; ++i) {
                    z += Math.pow(I_sq[i], ell) * a2[i] * Math.exp(-I_sq[i] * Math.pow(Math.PI, 2) * t);
                }
                double f = 0.5 * Math.pow(Math.PI, 2 * ell) * z;
                if (f <= 0) {
                    return -1;
                }
                for (int s = ell - 1; s >= 2; --s) {
                    double oddProd = 1;
                    for (int j = 3; j < 2 * s + 1; j += 2) {
                        oddProd *= j;
                    }
                    double K0 = oddProd / Math.sqrt(2 * Math.PI);
                    double cst = (1 + Math.pow(0.5, s + 0.5)) / 3;
                    double time = Math.pow(2 * cst * K0 / (N * f), 2.0 / (3 + 2 * s));

                    z = 0;
                    for (int i = 0; i < I_sq.length; ++i) {
                        z += Math.pow(I_sq[i], s) * a2[i] * Math.exp(-I_sq[i] * Math.pow(Math.PI, 2) * time);
                    }
                    f = 0.5 * Math.pow(Math.PI, 2 * s) * z;
                }

                double tOpt = Math.pow(2 * N * Math.sqrt(Math.PI) * f, -2.0 / 5);
                return t - tOpt;
            }, N);
            return Math.sqrt(tStar) * range;
        }
    }
}


