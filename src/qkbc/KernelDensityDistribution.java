package qkbc;

import umontreal.ssj.probdist.BetaDist;
import umontreal.ssj.probdist.ContinuousDistribution;
import umontreal.ssj.probdist.NormalDist;
import umontreal.ssj.probdist.TriangularDist;
import umontreal.ssj.stat.density.DEKernelDensity;

import java.util.Arrays;

public class KernelDensityDistribution extends ContinuousDistribution {
    private DEKernelDensity kd;

    private KernelDensityDistribution() {
    }

    public static KernelDensityDistribution buildKDDWithNormalKernel(double h, double[] data) {
        double[] sortedData = Arrays.copyOf(data, data.length);
        Arrays.sort(sortedData);
        KernelDensityDistribution d = new KernelDensityDistribution();
        d.kd = new DEKernelDensity(new NormalDist(), h, sortedData);
        return d;
    }

    public static KernelDensityDistribution buildKDDWithEpanechnikovKernel(double h, double[] data) {
        double[] sortedData = Arrays.copyOf(data, data.length);
        Arrays.sort(sortedData);
        KernelDensityDistribution d = new KernelDensityDistribution();
        d.kd = new DEKernelDensity(new BetaDist(2, 2, -1, 1), h, sortedData);
        return d;
    }

    public static KernelDensityDistribution buildKDDWithTriangularKernel(double h, double[] data) {
        double[] sortedData = Arrays.copyOf(data, data.length);
        Arrays.sort(sortedData);
        KernelDensityDistribution d = new KernelDensityDistribution();
        d.kd = new DEKernelDensity(new TriangularDist(-1, 1, 0), h, sortedData);
        return d;
    }

    @Override
    public double density(double v) {
        return kd.evalDensity(v);
    }

    @Override
    public double cdf(double v) {
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
}
