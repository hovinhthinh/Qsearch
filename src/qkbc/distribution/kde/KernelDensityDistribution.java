package qkbc.distribution.kde;

import qkbc.distribution.IntegralDistributionApproximator;
import umontreal.ssj.probdist.BetaDist;
import umontreal.ssj.probdist.ContinuousDistribution;
import umontreal.ssj.probdist.NormalDist;
import umontreal.ssj.probdist.TriangularDist;
import umontreal.ssj.stat.density.DEKernelDensity;
import util.Constants;

import java.util.Arrays;

public class KernelDensityDistribution extends ContinuousDistribution {
    private DEKernelDensity kd;
    private IntegralDistributionApproximator distApproximator;

    private KernelDensityDistribution() {
    }

    public static KernelDensityDistribution buildKDDWithNormalKernel(double h, double[] data) {
        double[] sortedData = Arrays.copyOf(data, data.length);
        Arrays.sort(sortedData);
        KernelDensityDistribution d = new KernelDensityDistribution();
        d.kd = new DEKernelDensity(new NormalDist(), h, sortedData);
        d.distApproximator = new IntegralDistributionApproximator(d);
        return d;
    }

    public static KernelDensityDistribution buildKDDWithEpanechnikovKernel(double h, double[] data) {
        double[] sortedData = Arrays.copyOf(data, data.length);
        Arrays.sort(sortedData);
        KernelDensityDistribution d = new KernelDensityDistribution();
        d.kd = new DEKernelDensity(new BetaDist(2, 2, -1, 1), h, sortedData);
        d.distApproximator = new IntegralDistributionApproximator(d);
        return d;
    }

    public static KernelDensityDistribution buildKDDWithTriangularKernel(double h, double[] data) {
        double[] sortedData = Arrays.copyOf(data, data.length);
        Arrays.sort(sortedData);
        KernelDensityDistribution d = new KernelDensityDistribution();
        d.kd = new DEKernelDensity(new TriangularDist(-1, 1, 0), h, sortedData);
        d.distApproximator = new IntegralDistributionApproximator(d);
        return d;
    }

    @Override
    public double density(double v) {
        return kd.evalDensity(v);
    }

    public double trueCdf(double v) {
        double cdf = 0;
        ContinuousDistribution kernel = kd.getKernel();
        double bandwidth = kd.getH();
        double[] data = kd.getData();
        for (double d : kd.getData()) {
            cdf += kernel.cdf((v - d) / bandwidth);
        }
        cdf /= data.length;
        return cdf;
    }

    @Override
    public double cdf(double v) {
        return distApproximator.getEstimatedCdf(v);
    }

    @Override
    public double[] getParams() {
        return new double[0];
    }

    public DEKernelDensity getEstimator() {
        return kd;
    }

    @Override
    public String toString() {
        return kd.toString();
    }

    @Override
    public double getMean() {
        return inverseF(0.5);
    }

    @Override
    public double inverseF(double u) {
        double l = Constants.MIN_DOUBLE, r = Constants.MAX_DOUBLE;

        while (r - l > Constants.EPS) {
            double mid = (l + r) / 2;
            if (trueCdf(mid) <= u) {
                l = mid;
            } else {
                r = mid;
            }
        }

        return l;
    }
}
