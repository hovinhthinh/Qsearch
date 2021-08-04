package qkbc.distribution;


import qkbc.distribution.kde.BandwidthSelector;
import qkbc.distribution.kde.KDEDistribution;
import qkbc.distribution.kde.ReflectKDEDistribution;
import umontreal.ssj.gof.GofStat;
import umontreal.ssj.probdist.*;
import util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class DistributionFitter {
    public static final List<Class<? extends ContinuousDistribution>> PARAMETRIC_CONTINUOUS_DIST_TYPES = Arrays.asList(
            NormalDist.class,
            ExponentialDist.class,
            GammaDist.class,
            BetaDist.class,
            WeibullDist.class,
            LognormalDist.class,
            GumbelDist.class,
            ParetoDist.class,
            InverseGaussianDist.class
    );

    public static final List<Class<? extends ContinuousDistribution>> NON_PARAMETRIC_CONTINUOUS_DIST_TYPES = Arrays.asList(
            KDEDistribution.class,
            ReflectKDEDistribution.class
    );

    public static double getPValueFromSamples(ContinuousDistribution d, double[] samples) {
        /*
        double[] pValues = new double[3];
        GofStat.kolmogorovSmirnov(samples, d, new double[3], pValues);
        return pValues[2];
        */
        return GofStat.andersonDarling(samples, d)[1];
    }

    // return Pair<dist, pValue>
    public static Pair<ContinuousDistribution, Double> fitParametricContinuous(double[] values, Class<? extends ContinuousDistribution> distType) {
        Pair<ContinuousDistribution, Double> bestDist = null;

        for (Class<? extends ContinuousDistribution> c : distType == null ? PARAMETRIC_CONTINUOUS_DIST_TYPES : Arrays.asList(distType)) {
            try {
                ContinuousDistribution d = (ContinuousDistribution) c.getMethod("getInstanceFromMLE", double[].class, int.class)
                        .invoke(null, values, values.length);

                double pValue = getPValueFromSamples(d, values);
                if (bestDist == null || pValue > bestDist.second) {
                    bestDist = new Pair<>(d, pValue);
                }
            } catch (NoSuchMethodException | SecurityException e) {
                e.printStackTrace();
            } catch (Exception e) {
            }
        }

        return bestDist;
    }

    // return Pair<dist, pValue>
    public static Pair<ContinuousDistribution, Double> fitParametricContinuous(List<Double> values, Class<? extends ContinuousDistribution> distType) {
        return fitParametricContinuous(values.stream().mapToDouble(Double::doubleValue).toArray(), distType);
    }

    public static Pair<ContinuousDistribution, Double> fitParametricContinuous(double[] values) {
        return fitParametricContinuous(values, null);
    }

    public static Pair<ContinuousDistribution, Double> fitParametricContinuous(List<Double> values) {
        return fitParametricContinuous(values, null);
    }

    public static Pair<ContinuousDistribution, Double> fitNonParametricContinuous(List<Double> values, Class<? extends ContinuousDistribution> distType) {
        return fitNonParametricContinuous(values.stream().mapToDouble(Double::doubleValue).toArray(), distType);
    }

    public static Pair<ContinuousDistribution, Double> fitNonParametricContinuous(double[] values, Class<? extends ContinuousDistribution> distType) {
        Pair<ContinuousDistribution, Double> bestDist = null;

        for (Class<? extends ContinuousDistribution> c : distType == null ? NON_PARAMETRIC_CONTINUOUS_DIST_TYPES : Arrays.asList(distType)) {
            try {
                ContinuousDistribution d = (ContinuousDistribution) c.getMethod("buildKDDWithNormalKernel", BandwidthSelector.class, double[].class)
                        .invoke(null, new BandwidthSelector.Silverman(), values);

                double pValue = getPValueFromSamples(d, values);
                if (bestDist == null || pValue > bestDist.second) {
                    bestDist = new Pair<>(d, pValue);
                }
            } catch (NoSuchMethodException | SecurityException e) {
                e.printStackTrace();
            } catch (Exception e) {
            }
        }

        return bestDist;
    }


    public static Pair<ContinuousDistribution, Double> fitNonParametricContinuous(List<Double> values, Class<? extends ContinuousDistribution> distType, double h) {
        return fitNonParametricContinuous(values.stream().mapToDouble(Double::doubleValue).toArray(), distType, h);

    }

    public static Pair<ContinuousDistribution, Double> fitNonParametricContinuous(double[] values, Class<? extends ContinuousDistribution> distType, double h) {
        try {
            ContinuousDistribution d = (ContinuousDistribution) distType.getMethod("buildKDDWithNormalKernel", double.class, double[].class)
                    .invoke(null, h, values);

            double pValue = getPValueFromSamples(d, values);
            return new Pair<>(d, pValue);
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        } catch (Exception e) {
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Random r = new Random((int) 1e9 + 7);
        Distribution d = new NormalDist(0, 1);

        ArrayList<Double> samples = new ArrayList<>();
        for (int i = 0; i < 1000; ++i) {
            samples.add(d.inverseF(r.nextDouble()));
        }

        Pair<ContinuousDistribution, Double> dist = fitNonParametricContinuous(samples, null);

        System.out.println(dist);

        new DistributionPresenter(null, dist.first, samples.stream().mapToDouble(o->o).toArray(),
                true, true, true, true)
                .present(true);
    }
}
