package qkbc.text;

import qkbc.DistributionFitter;
import umontreal.ssj.probdist.ContinuousDistribution;
import umontreal.ssj.probdist.Distribution;
import umontreal.ssj.probdist.NormalDist;
import util.Constants;
import util.Number;

import java.util.*;
import java.util.stream.Collectors;

public class RelationInstanceNoiseFilter {
    public static final int N_FOLD = 10000;
    public static final double SAMPLING_RATE = 0.9;
    public static final int MIN_SAMPLING_SIZE = 20;
    public static final double NOISE_PVALUE_RELDIST_THRESHOLD = 0.1;

    // do not allow duplicated sample values for the same entity
    private static ArrayList<Double> extractDistributionSamplesFromRelationInstances(List<RelationInstance> ri) {
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

    public static void consistencyBasedDistributionNoiseFilter(ArrayList<RelationInstance> ri) {
        for (RelationInstance i : ri) {
            i.positive = true;
        }

        ContinuousDistribution originalDist = DistributionFitter.fitContinuous(extractDistributionSamplesFromRelationInstances(ri)).first;

        // compute original p-values
        HashMap<String, Double> kbcId2OriginalPValue = new HashMap<>() {{
            for (RelationInstance i : ri) {
                put(i.kbcId, DistributionFitter.getPValueFromSample(originalDist, i.quantityStdValue));
            }
        }};

        // compute consistency p-values
        int sampleSize = (int) (ri.size() * SAMPLING_RATE + Constants.EPS);
        if (sampleSize < MIN_SAMPLING_SIZE) {
            return;
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
            ContinuousDistribution sampleDist = DistributionFitter.fitContinuous(sampleValues, originalDist.getClass()).first;
            for (int j = sampleSize; j < riCopy.size(); ++j) {
                RelationInstance ins = riCopy.get(j);
                kbcId2ConsistencyTimeCount.put(ins.kbcId, kbcId2ConsistencyTimeCount.getOrDefault(ins.kbcId, 0) + 1);
                kbcId2ConsistencySumPValue.put(ins.kbcId,
                        kbcId2ConsistencySumPValue.getOrDefault(ins.kbcId, 0.0) + DistributionFitter.getPValueFromSample(sampleDist, ins.quantityStdValue));
            }
        }

        // too small number of samples, or too many duplicated values, so that the computation is invalid.
        if (kbcId2ConsistencyTimeCount.size() < ri.size()) {
            return;
        }

        for (RelationInstance i : ri) {
            double diff = Number.relativeNumericDistance(kbcId2OriginalPValue.get(i.kbcId),
                    kbcId2ConsistencySumPValue.get(i.kbcId) / kbcId2ConsistencyTimeCount.get(i.kbcId));
            i.positive = diff < NOISE_PVALUE_RELDIST_THRESHOLD && kbcId2OriginalPValue.get(i.kbcId) > Constants.EPS;

//            System.out.printf("%10.3f    oP: %.3f    cP: %.3f    %.3f    %s\n", i.quantityStdValue,
//                    kbcId2OriginalPValue.get(i.kbcId),
//                    kbcId2ConsistencySumPValue.get(i.kbcId) / kbcId2ConsistencyTimeCount.get(i.kbcId),
//                    diff, i.positive ? "" : "noise");
        }
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

        consistencyBasedDistributionNoiseFilter(samples);
    }
}
