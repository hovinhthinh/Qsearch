package qkbc.distribution.kde;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import umontreal.ssj.probdist.EmpiricalDist;

public interface BandwidthSelector {
    double compute(double[] samples);

    // Preferred for samples generated from normal dist, with normal dist kernel
    class Scott implements BandwidthSelector {
        private static double CONST = Math.pow(4.0 / 3, 1.0 / 5);

        @Override
        public double compute(double[] samples) {
            if (samples.length <= 1) {
                return 0;
            }
            return CONST * (new EmpiricalDist(samples).getSampleStandardDeviation()) * Math.pow(samples.length, -1.0 / 5);
        }
    }

    // Preferred for samples generated from normal dist, with normal dist kernel (but more robust than Scott's rule of thumb)
    class Silverman implements BandwidthSelector {

        @Override
        public double compute(double[] samples) {
            if (samples.length <= 1) {
                return 0;
            }
            DescriptiveStatistics ds = new DescriptiveStatistics(samples);
            double iqr = ds.getPercentile(75) - ds.getPercentile(25);

            return 0.9 * Math.min(new EmpiricalDist(samples).getSampleStandardDeviation(), iqr / 1.349)
                    * Math.pow(samples.length, -1.0 / 5);
        }
    }
}


