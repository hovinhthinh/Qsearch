package qkbc;


import umontreal.ssj.gof.GofStat;
import umontreal.ssj.probdist.*;
import util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

;

public class DistributionFitter {
    public static double getPValueFromSamples(ContinuousDistribution d, double[] samples) {
        /*
        double[] pValues = new double[3];
        GofStat.kolmogorovSmirnov(samples, d, new double[3], pValues);
        return pValues[2];
        */
        return GofStat.andersonDarling(samples, d)[1];
    }

    public static double getPValueFromSample(ContinuousDistribution d, double sample) {
        return getPValueFromSamples(d, new double[]{sample});
    }

    // return Pair<dist, pValue>
    public static Pair<ContinuousDistribution, Double> fitContinuous(double[] values, Class<? extends ContinuousDistribution> distType) {
        ContinuousDistribution bestDist = null;
        double bestPValue = -1;

        ArrayList<ContinuousDistribution> distTypes = new ArrayList<>() {{
            try {
                if (distType == null || distType.equals(NormalDist.class)) {
                    add(NormalDist.getInstanceFromMLE(values, values.length));
                }
            } catch (Exception e) {
            }
            try {
                if (distType == null || distType.equals(ExponentialDist.class)) {
                    add(ExponentialDist.getInstanceFromMLE(values, values.length));
                }
            } catch (Exception e) {
            }
            try {
                if (distType == null || distType.equals(GammaDist.class)) {
                    add(GammaDist.getInstanceFromMLE(values, values.length));
                }
            } catch (Exception e) {
            }
            try {
                if (distType == null || distType.equals(WeibullDist.class)) {
                    add(WeibullDist.getInstanceFromMLE(values, values.length));
                }
            } catch (Exception e) {
            }
        }};

        for (ContinuousDistribution d : distTypes) {
            try {
                double pValue = getPValueFromSamples(d, values);
                if (bestDist == null || pValue > bestPValue) {
                    bestPValue = pValue;
                    bestDist = d;
                }
            } catch (Exception e) {
            }
        }

        return new Pair<>(bestDist, bestPValue);
    }

    // return Pair<dist, pValue>
    public static Pair<ContinuousDistribution, Double> fitContinuous(List<Double> values, Class<? extends ContinuousDistribution> distType) {
        return fitContinuous(values.stream().mapToDouble(Double::doubleValue).toArray(), distType);
    }

    public static Pair<ContinuousDistribution, Double> fitContinuous(double[] values) {
        return fitContinuous(values, null);
    }

    public static Pair<ContinuousDistribution, Double> fitContinuous(List<Double> values) {
        return fitContinuous(values, null);
    }

    public static void main(String[] args) {
        Random r = new Random();
        Distribution d = new NormalDist(0, 1);

        ArrayList<Double> samples = new ArrayList<>();
        for (int i = 0; i < 100; ++i) {
            samples.add(d.inverseF(r.nextDouble()));
        }

        Pair<ContinuousDistribution, Double> dist = fitContinuous(samples);

        System.out.println(dist.first);
        System.out.println(dist.second);
    }

}
