package qkbc.distribution.kde;

import umontreal.ssj.probdist.BetaDist;
import umontreal.ssj.probdist.ContinuousDistribution;
import umontreal.ssj.probdist.NormalDist;
import umontreal.ssj.probdist.TriangularDist;

// support [0, inf)
public class ReflectKDEDistribution extends KDEDistribution {
    public ReflectKDEDistribution(ContinuousDistribution kernel, double bandwidth, double[] data) {
        super(kernel, bandwidth, data);
    }

    public static ReflectKDEDistribution buildKDDWithNormalKernel(BandwidthSelector bws, double[] data) {
        return buildKDDWithNormalKernel(bws.compute(data), data);
    }

    public static ReflectKDEDistribution buildKDDWithNormalKernel(double h, double[] data) {
        return new ReflectKDEDistribution(new NormalDist(), h, data);
    }

    public static ReflectKDEDistribution buildKDDWithEpanechnikovKernel(double h, double[] data) {
        return new ReflectKDEDistribution(new BetaDist(2, 2, -1, 1), h, data);
    }

    public static ReflectKDEDistribution buildKDDWithTriangularKernel(double h, double[] data) {
        return new ReflectKDEDistribution(new TriangularDist(-1, 1, 0), h, data);
    }

    @Override
    public double density(double v) {
        if (v < 0) {
            return 0;
        }
        return kd.evalDensity(v) + kd.evalDensity(-v);
    }

    public double trueCdf(double v) {
        if (v < 0) {
            return 0;
        }
        return super.trueCdf(v) - super.trueCdf(-v);
    }

    @Override
    public String toString() {
        return "[Reflect] " + kd.toString();
    }
}
