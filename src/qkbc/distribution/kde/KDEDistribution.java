package qkbc.distribution.kde;

import qkbc.distribution.IntegralDistributionApproximator;
import umontreal.ssj.probdist.BetaDist;
import umontreal.ssj.probdist.ContinuousDistribution;
import umontreal.ssj.probdist.NormalDist;
import umontreal.ssj.probdist.TriangularDist;
import umontreal.ssj.stat.density.DEKernelDensity;
import util.Constants;

import java.util.Arrays;

public class KDEDistribution extends ContinuousDistribution {
    protected DEKernelDensity kd;
    protected IntegralDistributionApproximator distApproximator;
    protected ContinuousDistribution kernel;
    protected double h;

    public KDEDistribution(ContinuousDistribution kernel, double bandwidth, double[] data) {
        double[] sortedData = Arrays.copyOf(data, data.length);
        Arrays.sort(sortedData);
        this.kernel = kernel;
        this.h = bandwidth;
        this.kd = new DEKernelDensity(kernel, bandwidth, sortedData);
        this.distApproximator = new IntegralDistributionApproximator(this);
    }

    public static KDEDistribution buildKDDWithNormalKernel(BandwidthSelector bws, double[] data) {
        return buildKDDWithNormalKernel(bws.compute(data), data);
    }

    public static KDEDistribution buildKDDWithNormalKernel(double h, double[] data) {
        return new KDEDistribution(new NormalDist(), h, data);
    }

    public static KDEDistribution buildKDDWithEpanechnikovKernel(double h, double[] data) {
        return new KDEDistribution(new BetaDist(2, 2, -1, 1), h, data);
    }

    public static KDEDistribution buildKDDWithTriangularKernel(double h, double[] data) {
        return new KDEDistribution(new TriangularDist(-1, 1, 0), h, data);
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

    public double getH() {
        return h;
    }

    public ContinuousDistribution getKernel() {
        return kernel;
    }
}
