package qkbc.text;

import qkbc.DistributionFitter;
import umontreal.ssj.probdist.ContinuousDistribution;
import umontreal.ssj.probdist.Distribution;
import umontreal.ssj.probdist.NormalDist;
import util.Constants;
import util.Number;
import util.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class RelationInstanceNoiseFilter {
    public static final int N_FOLD = 10000;
    public static final double SAMPLING_RATE = 0.9;
    public static final int MIN_SAMPLING_SIZE = 20;
    public static final double NOISE_PVALUE_RELDIST_THRESHOLD = 0.1;
    public static final double MAX_NOISE_RATE = 0.1;

    // do not allow duplicated sample values for the same entity
    public static ArrayList<Double> extractDistributionSamplesFromRelationInstances(List<RelationInstance> ri) {
        ArrayList<Double> samples = new ArrayList<>();
        ri.stream().collect(Collectors.groupingBy(i -> i.entity)).forEach((e, eri) -> {
            ArrayList<Double> entitySamples = new ArrayList<>();
            loop:
            for (RelationInstance i : eri) {
                for (Double s : entitySamples) {
                    if (Number.relativeNumericDistance(s, i.quantityStdValue) <= 0.01) {
                        continue loop;
                    }
                }
                entitySamples.add(i.quantityStdValue);
            }
            samples.addAll(entitySamples);
        });
        return samples;
    }

    private static Pair<ContinuousDistribution, Double> consistencyBasedDistributionNoiseFilter(
            ArrayList<RelationInstance> ri, Class<? extends ContinuousDistribution> distType) {
        for (RelationInstance i : ri) {
            i.positive = true;
        }

        ArrayList<Double> distSamples = extractDistributionSamplesFromRelationInstances(ri);
        Pair<ContinuousDistribution, Double> originalDist = DistributionFitter.fitContinuous(distSamples, distType);
//        System.out.println(originalDist);
        if (originalDist == null) {
            return null;
        }

        // compute original p-values
        HashMap<String, Double> kbcId2OriginalPValue = new HashMap<>() {{
            for (RelationInstance i : ri) {
                put(i.kbcId, DistributionFitter.getPValueFromSample(originalDist.first, i.quantityStdValue));
            }
        }};

        // compute consistency p-values
        int sampleSize = (int) (ri.size() * SAMPLING_RATE + Constants.EPS);
        if (sampleSize < MIN_SAMPLING_SIZE) {
            return originalDist;
        }

        HashMap<String, Integer> kbcId2ConsistencyTimeCount = new HashMap<>();
        HashMap<String, Double> kbcId2ConsistencySumPValue = new HashMap<>();

        ArrayList<RelationInstance> riCopy = new ArrayList<>(ri);
        for (int i = 0; i < N_FOLD; ++i) {
            Collections.shuffle(riCopy);
            ArrayList<Double> sampleValues = extractDistributionSamplesFromRelationInstances(riCopy.subList(0, sampleSize));
            if (sampleValues.size() < MIN_SAMPLING_SIZE) {
                continue;
            }
            ContinuousDistribution sampleDist = DistributionFitter.fitContinuous(sampleValues, originalDist.first.getClass()).first;
            for (int j = sampleSize; j < riCopy.size(); ++j) {
                RelationInstance ins = riCopy.get(j);
                kbcId2ConsistencyTimeCount.put(ins.kbcId, kbcId2ConsistencyTimeCount.getOrDefault(ins.kbcId, 0) + 1);
                kbcId2ConsistencySumPValue.put(ins.kbcId,
                        kbcId2ConsistencySumPValue.getOrDefault(ins.kbcId, 0.0) + DistributionFitter.getPValueFromSample(sampleDist, ins.quantityStdValue));
            }
        }

        // too small number of samples, or too many duplicated values, so that the computation is invalid.
        if (kbcId2ConsistencyTimeCount.size() < ri.size()) {
            return originalDist;
        }

        for (RelationInstance i : ri) {
            double diff = Number.relativeNumericDistance(kbcId2OriginalPValue.get(i.kbcId),
                    kbcId2ConsistencySumPValue.get(i.kbcId) / kbcId2ConsistencyTimeCount.get(i.kbcId));
            i.positive = diff < NOISE_PVALUE_RELDIST_THRESHOLD && kbcId2OriginalPValue.get(i.kbcId) > 1e-4;

//            System.out.printf("%10.3f    oP: %.3f    cP: %.3f    %.3f    %s\n", i.quantityStdValue,
//                    kbcId2OriginalPValue.get(i.kbcId),
//                    kbcId2ConsistencySumPValue.get(i.kbcId) / kbcId2ConsistencyTimeCount.get(i.kbcId),
//                    diff, i.positive ? "" : "noise");
        }

        // if noise rate is too high, then the test becomes invalid
        double noiseRate = 1.0 * extractDistributionSamplesFromRelationInstances(ri.stream().filter(i -> !i.positive)
                .collect(Collectors.toList())).size() / distSamples.size();
//        System.out.println(String.format("Noise rate: %.3f", noiseRate));
        if (noiseRate > MAX_NOISE_RATE) {
            for (RelationInstance i : ri) {
                i.positive = true;
            }
            return originalDist;
        } else {
            // fit positive instances only
            return DistributionFitter.fitContinuous(
                    extractDistributionSamplesFromRelationInstances(ri.stream().filter(i -> i.positive).collect(Collectors.toList())),
                    originalDist.first.getClass());
        }
    }

    public static Pair<ContinuousDistribution, Double> consistencyBasedDistributionNoiseFilter(ArrayList<RelationInstance> ri) {
        Pair<ContinuousDistribution, Double> bestDist = null;

        for (Class<? extends ContinuousDistribution> c : DistributionFitter.CONTINUOUS_DIST_TYPES) {
            Pair<ContinuousDistribution, Double> d = consistencyBasedDistributionNoiseFilter(ri, c);

            if (d != null && (bestDist == null || d.second > bestDist.second)) {
                bestDist = d;
            }
        }

        return bestDist;
    }

    public static void main(String[] args) {
        Random r = new Random();
        Distribution d = new NormalDist(0, 1);

        ArrayList<RelationInstance> samples = new ArrayList<>();
        int nSample = 30, nNoise = 2;

        for (int i = 0; i < nSample; ++i) {
            samples.add(new RelationInstance(i + "", null, d.inverseF(r.nextDouble()), 0, i + ""));
        }
        for (int i = 0; i < nNoise; ++i) {
            samples.add(new RelationInstance(i + nSample + "", null, (r.nextDouble() - 0.5) * 100, 0, i + nSample + ""));
        }

        System.out.println(consistencyBasedDistributionNoiseFilter(samples));
    }
}
