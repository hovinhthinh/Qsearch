package eval.qkbc.exp_2;

import eval.qkbc.QKBCResult;
import model.quantity.Quantity;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import qkbc.text.RelationInstance;
import qkbc.text.RelationInstanceNoiseFilter;
import umontreal.ssj.probdist.EmpiricalDist;
import util.FileUtils;
import util.Gson;
import util.Number;
import util.Pair;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotationGeneratorAndSummerization_Noise {
    public static class DoQStats {
        public double mean, std;

        public DoQStats(double mean, double std) {
            this.mean = mean;
            this.std = std;
        }
    }

    // do not allow duplicated sample values for the same entity
    public static ArrayList<RelationInstance> deduplicateNoise(List<RelationInstance> ri) {
        ArrayList<RelationInstance> r = new ArrayList<>();
        ri.stream().collect(Collectors.groupingBy(i -> i.entity)).forEach((e, eri) -> {
            ArrayList<RelationInstance> entityR = new ArrayList<>();
            loop:
            for (RelationInstance i : eri) {
                for (RelationInstance s : entityR) {
                    if (Number.relativeNumericDistance(s.quantityStdValue, i.quantityStdValue) <= RelationInstanceNoiseFilter.DUPLICATED_DIFF_RATE) {
                        continue loop;
                    }
                }
                entityR.add(i);
            }
            r.addAll(entityR);
        });
        return r;
    }

    private static Random RANDOM = new Random(6993);

    public static void sample(List<RelationInstance> ri) {
        Collections.shuffle(ri, RANDOM);
        if (ri.size() > QKBCResult.ANNOTATION_SAMPLING_SIZE) {
            ri.subList(QKBCResult.ANNOTATION_SAMPLING_SIZE, ri.size()).clear();
        }
    }

    public static ArrayList<RelationInstance> dbScanNoiseDetect(ArrayList<RelationInstance> ris) {
        ArrayList<Pair<Double, Integer>> arr = new ArrayList<>();
        double max = ris.stream().mapToDouble(o -> Math.abs(o.quantityStdValue)).max().getAsDouble();
        for (int i = 0; i < ris.size(); ++i) {
            arr.add(new Pair<>(ris.get(i).quantityStdValue / max, i));
        }
        Collections.sort(arr, Comparator.comparing(a -> a.first));

        double EPS = 0.5, MIN_SAMPLES = 5;
        boolean[] isCore = new boolean[ris.size()];
        boolean[] isNoise = new boolean[ris.size()];

        for (int i = 0; i < ris.size(); ++i) {
            int nPoint = 1;
            int cur = i - 1;
            while (nPoint < MIN_SAMPLES && cur >= 0 && arr.get(i).first - arr.get(cur).first <= EPS) {
                ++nPoint;
                --cur;
            }
            cur = i + 1;
            while (nPoint < MIN_SAMPLES && cur < arr.size() && arr.get(cur).first - arr.get(i).first <= EPS) {
                ++nPoint;
                ++cur;
            }
            if (nPoint >= MIN_SAMPLES) {
                isCore[i] = true;
            }
        }

        loop:
        for (int i = 0; i < ris.size(); ++i) {
            isNoise[i] = true;
            for (int j = i; j >= 0; --j) {
                if (arr.get(i).first - arr.get(j).first > EPS) {
                    break;
                }
                if (isCore[j]) {
                    isNoise[i] = false;
                    continue loop;
                }
            }
            for (int j = i; j < arr.size(); ++j) {
                if (arr.get(j).first - arr.get(i).first > EPS) {
                    break;
                }
                if (isCore[j]) {
                    isNoise[i] = false;
                    continue loop;
                }
            }
        }

        ArrayList<RelationInstance> res = new ArrayList<>();
        for (int i = 0; i < arr.size(); ++i) {
            if (isNoise[i]) {
                res.add(ris.get(arr.get(i).second));
            }
        }
        return res;
    }

    public static Map<String, List<String>> markNoise(QKBCResult r, int baseIter, String annotatedFile, DoQStats doq) throws Exception {
        Map<String, List<String>> map = new HashMap<>();

        ArrayList<RelationInstance> ris = r.instances.stream()
                .filter(ri -> ri.positiveIterIndices.contains(baseIter) || ri.noiseIterIndices.contains(baseIter))
                .filter(ri -> !r.refinementByTime || ri.getYearCtx() != null)
                .collect(Collectors.toCollection(ArrayList::new));

        HashMap<String, Boolean> kbcId2Eval = null;
        if (annotatedFile != null) {
            kbcId2Eval = new HashMap<>();
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(new FileReader(annotatedFile, StandardCharsets.UTF_8));
            for (CSVRecord o : records) {
                String id = o.get(0);
                String eval = o.get(7);
                if (eval.equals("TRUE")) {
                    kbcId2Eval.put(id, true);
                } else if (eval.equals("FALSE")) {
                    kbcId2Eval.put(id, false);
                } else {
                    throw new RuntimeException("invalid eval: " + annotatedFile + " @ " + id);
                }
            }
        }

        // ours
        ArrayList<RelationInstance> deduplicatedOurs = new ArrayList<>();
        for (RelationInstance ri : ris) {
            if (ri.noiseIterIndices.contains(baseIter)) {
                deduplicatedOurs.add(ri);
            }
        }
        deduplicatedOurs = deduplicateNoise(deduplicatedOurs);
        System.out.println("#Ours: " + deduplicatedOurs.size());
        List<RelationInstance> groundtruth = deduplicatedOurs.stream().filter(ri -> ri.groundtruth != null).collect(Collectors.toList());
        int nTrueGt = (int) groundtruth.stream().filter(ri -> ri.groundtruth).count();
        System.out.println("groundtruth: " + nTrueGt + "/" + groundtruth.size());

        ArrayList<RelationInstance> gtUnavail = deduplicatedOurs.stream().filter(ri -> ri.groundtruth == null)
                .collect(Collectors.toCollection(ArrayList::new));
        int nGtUnavail = gtUnavail.size();
        System.out.println("groundtruthUnavail: " + gtUnavail.size());
        sample(gtUnavail);

        // print prec
        if (annotatedFile != null) {
            int nTrueUnavail = 0;
            for (RelationInstance ri : gtUnavail) {
                if (kbcId2Eval.get(ri.kbcId)) {
                    ++nTrueUnavail;
                }
            }

            double prec = 1 - ((1.0 * nTrueUnavail / gtUnavail.size()) * nGtUnavail + nTrueGt) / (groundtruth.size() + nGtUnavail);
            System.out.println(String.format("---- Noise prec: %.3f", prec));
        }

        gtUnavail.forEach(ri -> {
            map.put(ri.kbcId, new ArrayList<>(Arrays.asList("ours")));
        });

        // percentile & range
        Collections.sort(ris, Comparator.comparing(o -> o.quantityStdValue));
        double top = 1.0 * ris.size() / 100;
        int nTail_1 = (int) top;

        double begin = ris.get(0).quantityStdValue, end = ris.get(ris.size() - 1).quantityStdValue;
        double interval = (end - begin) / 100;

        EmpiricalDist dist = new EmpiricalDist(ris.stream().mapToDouble(o -> o.quantityStdValue).toArray());

        ArrayList<RelationInstance> deduplicatedPrct = new ArrayList<>(),
                deduplicatedRng = new ArrayList<>(),
                deduplicatedZscr = new ArrayList<>(),
                deduplicatedDoQ = new ArrayList<>(),
                deduplicatedDbScan = new ArrayList<>();

        for (int i = 0; i < ris.size(); ++i) {
            if (i < nTail_1 || i >= ris.size() - nTail_1) {
                deduplicatedPrct.add(ris.get(i));
            }
            double v = ris.get(i).quantityStdValue;
            if (v <= begin + interval || v >= end - interval) {
                deduplicatedRng.add(ris.get(i));
            }
            double z = (v - dist.getMean()) / dist.getSampleStandardDeviation();
            if (Math.abs(z) > 3) {
                deduplicatedZscr.add(ris.get(i));
            }
            if (doq != null) {
                z = (v - doq.mean) / doq.std;
                if (Math.abs(z) > 3) {
                    deduplicatedDoQ.add(ris.get(i));
                }
            }
        }
        deduplicatedPrct = deduplicateNoise(deduplicatedPrct);
        System.out.println("#Prct@1: " + deduplicatedPrct.size());
        groundtruth = deduplicatedPrct.stream().filter(ri -> ri.groundtruth != null).collect(Collectors.toList());
        nTrueGt = (int) groundtruth.stream().filter(ri -> ri.groundtruth).count();
        System.out.println("groundtruth: " + nTrueGt + "/" + groundtruth.size());
        gtUnavail = deduplicatedPrct.stream().filter(ri -> ri.groundtruth == null)
                .collect(Collectors.toCollection(ArrayList::new));
        nGtUnavail = gtUnavail.size();
        System.out.println("groundtruthUnavail: " + gtUnavail.size());
        sample(gtUnavail);

        // print prec
        if (annotatedFile != null) {
            int nTrueUnavail = 0;
            for (RelationInstance ri : gtUnavail) {
                if (kbcId2Eval.get(ri.kbcId)) {
                    ++nTrueUnavail;
                }
            }

            double prec = 1 - ((1.0 * nTrueUnavail / gtUnavail.size()) * nGtUnavail + nTrueGt) / (groundtruth.size() + nGtUnavail);
            System.out.println(String.format("---- Noise prec: %.3f", prec));
        }

        gtUnavail.forEach(ri -> {
            map.putIfAbsent(ri.kbcId, new ArrayList<>());
            map.get(ri.kbcId).add("prct@1");
        });

        //
        deduplicatedRng = deduplicateNoise(deduplicatedRng);
        System.out.println("#Rng@1: " + deduplicatedRng.size());
        groundtruth = deduplicatedRng.stream().filter(ri -> ri.groundtruth != null).collect(Collectors.toList());
        nTrueGt = (int) groundtruth.stream().filter(ri -> ri.groundtruth).count();
        System.out.println("groundtruth: " + nTrueGt + "/" + groundtruth.size());
        gtUnavail = deduplicatedRng.stream().filter(ri -> ri.groundtruth == null)
                .collect(Collectors.toCollection(ArrayList::new));
        nGtUnavail = gtUnavail.size();
        System.out.println("groundtruthUnavail: " + gtUnavail.size());
        sample(gtUnavail);

        // print prec
        if (annotatedFile != null) {
            int nTrueUnavail = 0;
            for (RelationInstance ri : gtUnavail) {
                if (kbcId2Eval.get(ri.kbcId)) {
                    ++nTrueUnavail;
                }
            }

            double prec = 1 - ((1.0 * nTrueUnavail / gtUnavail.size()) * nGtUnavail + nTrueGt) / (groundtruth.size() + nGtUnavail);
            System.out.println(String.format("---- Noise prec: %.3f", prec));
        }

        gtUnavail.forEach(ri -> {
            map.putIfAbsent(ri.kbcId, new ArrayList<>());
            map.get(ri.kbcId).add("rng@1");
        });

        //
        deduplicatedZscr = deduplicateNoise(deduplicatedZscr);
        System.out.println("#Z-scr: " + deduplicatedZscr.size());
        groundtruth = deduplicatedZscr.stream().filter(ri -> ri.groundtruth != null).collect(Collectors.toList());
        nTrueGt = (int) groundtruth.stream().filter(ri -> ri.groundtruth).count();
        System.out.println("groundtruth: " + nTrueGt + "/" + groundtruth.size());
        gtUnavail = deduplicatedZscr.stream().filter(ri -> ri.groundtruth == null)
                .collect(Collectors.toCollection(ArrayList::new));
        nGtUnavail = gtUnavail.size();
        System.out.println("groundtruthUnavail: " + gtUnavail.size());
        sample(gtUnavail);

        // print prec
        if (annotatedFile != null) {
            int nTrueUnavail = 0;
            for (RelationInstance ri : gtUnavail) {
                if (kbcId2Eval.get(ri.kbcId)) {
                    ++nTrueUnavail;
                }
            }

            double prec = 1 - ((1.0 * nTrueUnavail / gtUnavail.size()) * nGtUnavail + nTrueGt) / (groundtruth.size() + nGtUnavail);
            System.out.println(String.format("---- Noise prec: %.3f", prec));
        }

        gtUnavail.forEach(ri -> {
            map.putIfAbsent(ri.kbcId, new ArrayList<>());
            map.get(ri.kbcId).add("z-scr");
        });

        //
        if (doq != null) {
            deduplicatedDoQ = deduplicateNoise(deduplicatedDoQ);
            System.out.println("#DoQ: " + deduplicatedDoQ.size());
            groundtruth = deduplicatedDoQ.stream().filter(ri -> ri.groundtruth != null).collect(Collectors.toList());
            nTrueGt = (int) groundtruth.stream().filter(ri -> ri.groundtruth).count();
            System.out.println("groundtruth: " + nTrueGt + "/" + groundtruth.size());
            gtUnavail = deduplicatedDoQ.stream().filter(ri -> ri.groundtruth == null)
                    .collect(Collectors.toCollection(ArrayList::new));
            nGtUnavail = gtUnavail.size();
            System.out.println("groundtruthUnavail: " + gtUnavail.size());
            sample(gtUnavail);

            // print prec
            if (annotatedFile != null) {
                int nTrueUnavail = 0;
                for (RelationInstance ri : gtUnavail) {
                    if (kbcId2Eval.get(ri.kbcId)) {
                        ++nTrueUnavail;
                    }
                }

                double prec = 1 - ((1.0 * nTrueUnavail / gtUnavail.size()) * nGtUnavail + nTrueGt) / (groundtruth.size() + nGtUnavail);
                System.out.println(String.format("---- Noise prec: %.3f", prec));
            }

            gtUnavail.forEach(ri -> {
                map.putIfAbsent(ri.kbcId, new ArrayList<>());
                map.get(ri.kbcId).add("doq");
            });
        }

        // DBScan
        deduplicatedDbScan = deduplicateNoise(dbScanNoiseDetect(ris));
        System.out.println("#Dbscan: " + deduplicatedDbScan.size());
        groundtruth = deduplicatedDbScan.stream().filter(ri -> ri.groundtruth != null).collect(Collectors.toList());
        nTrueGt = (int) groundtruth.stream().filter(ri -> ri.groundtruth).count();
        System.out.println("groundtruth: " + nTrueGt + "/" + groundtruth.size());
        gtUnavail = deduplicatedDbScan.stream().filter(ri -> ri.groundtruth == null)
                .collect(Collectors.toCollection(ArrayList::new));
        nGtUnavail = gtUnavail.size();
        System.out.println("groundtruthUnavail: " + gtUnavail.size());
        sample(gtUnavail);

        // print prec
        if (annotatedFile != null) {
            int nTrueUnavail = 0;
            for (RelationInstance ri : gtUnavail) {
                if (kbcId2Eval.get(ri.kbcId)) {
                    ++nTrueUnavail;
                }
            }

            double prec = 1 - ((1.0 * nTrueUnavail / gtUnavail.size()) * nGtUnavail + nTrueGt) / (groundtruth.size() + nGtUnavail);
            System.out.println(String.format("---- Noise prec: %.3f", prec));
        }

        gtUnavail.forEach(ri -> {
            map.putIfAbsent(ri.kbcId, new ArrayList<>());
            map.get(ri.kbcId).add("dbs");
        });
        return map;
    }

    public static void generateTsvForGoogleSpreadsheet_Ours(String inputFile, int iter,
                                                            String annotatedFile,
                                                            String outputFile, DoQStats doq) throws Exception {
        System.out.println("======== " + inputFile + " : iter@" + iter);
        QKBCResult r = Gson.fromJson(FileUtils.getContent(inputFile, "UTF-8"), QKBCResult.class);

        Map<String, List<String>> noiseMap = markNoise(r, iter, annotatedFile, doq);

        if (annotatedFile == null && outputFile != null) {
            CSVPrinter csvPrinter = new CSVPrinter(FileUtils.getPrintWriter(outputFile, "UTF-8"), CSVFormat.DEFAULT
                    .withHeader("id", "settings", "source", "entity", r.predicate, "sentence", "groundtruth", "eval"));

            r.instances.forEach(ri -> {
                if (!noiseMap.containsKey(ri.kbcId)) {
                    return;
                }

                Quantity q = Quantity.fromQuantityString(ri.quantity);
                String qStr = Number.getWrittenString(q.value, true);

                String entityStr = q.getKgUnit().entity;
                if (entityStr != null) {
                    qStr += " " + entityStr;
                }

                if (r.refinementByTime) {
                    qStr = "@" + ri.getYearCtx() + ": " + qStr;
                }

                String source = ri.getSource();
                source = source.substring(source.indexOf(":") + 1);
                source = source.substring(source.indexOf(":") + 1);

                try {
                    csvPrinter.printRecord(
                            ri.kbcId,
                            noiseMap.get(ri.kbcId).toString(),
                            source,
                            ri.entity,
                            qStr,
                            ri.getSentence(),
                            ri.groundtruth == null ? "" : ri.groundtruth,
                            ri.groundtruth == null && ri.eval == null ? "?" : ""
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            csvPrinter.close();
        }
    }

    public static void main(String[] args) throws Exception {
//        generateTsvForGoogleSpreadsheet_Ours(
//                "eval/qkbc/exp_1/qsearch_queries/our_output_fact/building_height_ourN.json", 5,
//                "eval/qkbc/exp_2/annotation/qkbc eval exp_2 - building_height.csv",
//                "eval/qkbc/exp_2/annotation/building_height-noise_gg.csv", null);
//
//        generateTsvForGoogleSpreadsheet_Ours(
//                "eval/qkbc/exp_1/qsearch_queries/our_output_fact/mountain_elevation_ourN.json", 10,
//                "eval/qkbc/exp_2/annotation/qkbc eval exp_2 - mountain_elevation.csv",
//                "eval/qkbc/exp_2/annotation/mountain_elevation-noise_gg.csv", null);
//
//        generateTsvForGoogleSpreadsheet_Ours(
//                "eval/qkbc/exp_1/qsearch_queries/our_output_fact/river_length_ourN.json", 5,
//                "eval/qkbc/exp_2/annotation/qkbc eval exp_2 - river_length.csv",
//                "eval/qkbc/exp_2/annotation/river_length-noise_gg.csv", null);
//
//        generateTsvForGoogleSpreadsheet_Ours(
//                "eval/qkbc/exp_1/qsearch_queries/our_output_fact/stadium_capacity_ourN.json", 6,
//                "eval/qkbc/exp_2/annotation/qkbc eval exp_2 - stadium_capacity.csv",
//                "eval/qkbc/exp_2/annotation/stadium_capacity-noise_gg.csv", null);
//
//        generateTsvForGoogleSpreadsheet_Ours(
//                "eval/qkbc/exp_1/qsearch_queries/our_output_fact/company_revenue_ourN.json", 4,
//                "eval/qkbc/exp_2/annotation/qkbc eval exp_2 - company_revenue.csv",
//                "eval/qkbc/exp_2/annotation/company_revenue-noise_gg.csv", null);


//        generateTsvForGoogleSpreadsheet_Ours(
//                "eval/qkbc/exp_1/qsearch_queries/our_output_fact/powerstation_capacity_ourN.json", 3,
//                "eval/qkbc/exp_2/annotation/qkbc eval exp_2 - powerstation_capacity.csv",
//                "eval/qkbc/exp_2/annotation/powerstation_capacity-noise_gg.csv",
//                new DoQStats(443157278.73, 371247766.015));
//
//        generateTsvForGoogleSpreadsheet_Ours(
//                "eval/qkbc/exp_1/qsearch_queries/our_output_fact/earthquake_magnitude_ourN.json", 5,
//                "eval/qkbc/exp_2/annotation/qkbc eval exp_2 - earthquake_magnitude.csv",
//                "eval/qkbc/exp_2/annotation/earthquake_magnitude-noise_gg.csv", null);
    }
}
