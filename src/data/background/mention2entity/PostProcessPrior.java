package data.background.mention2entity;

import nlp.NLP;
import util.FileUtils;
import util.Monitor;
import util.Pair;
import util.Triple;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Deprecated
public class PostProcessPrior {
    // <input> <output>
    public static void process_0(String[] args) {
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            String[] arr = line.split("\t");
            try {
                arr[0] = String.join(" ", NLP.tokenize(arr[0]));
            } catch (Exception e) {
            }
            out.println(arr[0] + "\t" + arr[1]);
        }
        out.close();
    }

    // <input> <output>
    public static void process_1(String[] args) {
        final AtomicInteger cnt = new AtomicInteger(0);
        Monitor monitor = new Monitor("process_1", -1, 10) {
            @Override
            public int getCurrent() {
                return cnt.get();
            }
        };
        monitor.start();


        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");

        String lastMention = null;
        Map<String, Integer> entity2Freq = null;

        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            Mention2EntityInfoLine infoLine = Mention2EntityInfoLine.fromLine(line);
            if (lastMention != null && !infoLine.mention.equals(lastMention)) {
                out.println(new Mention2EntityInfoLine(lastMention,
                        entity2Freq.entrySet().stream()
                                .map(o -> new Pair<>(o.getKey(), o.getValue()))
                                .sorted((o1, o2) -> o2.second.compareTo(o1.second))
                                .collect(Collectors.toCollection(ArrayList::new))
                ).toLine());

                lastMention = infoLine.mention;
                entity2Freq = infoLine.entityFreq.stream().collect(Collectors.toMap(o -> o.first, o -> o.second));
            } else {
                if (lastMention == null) {
                    lastMention = infoLine.mention;
                    entity2Freq = infoLine.entityFreq.stream().collect(Collectors.toMap(o -> o.first, o -> o.second));
                } else {
                    for (Triple<String, Integer, Double> o : infoLine.entityFreq) {
                        entity2Freq.put(o.first, entity2Freq.getOrDefault(o.first, 0) + o.second);
                    }
                }
            }
            cnt.incrementAndGet();
        }
        if (lastMention != null) {
            out.println(new Mention2EntityInfoLine(lastMention,
                    entity2Freq.entrySet().stream()
                            .map(o -> new Pair<>(o.getKey(), o.getValue()))
                            .sorted((o1, o2) -> o2.second.compareTo(o1.second))
                            .collect(Collectors.toCollection(ArrayList::new))
            ).toLine());
        }
        out.close();
        monitor.forceShutdown();
    }

    // <input> <output>
    public static void countEntity(String[] args) {
        final AtomicInteger cnt = new AtomicInteger(0);
        Monitor monitor = new Monitor("process_1", -1, 10) {
            @Override
            public int getCurrent() {
                return cnt.get();
            }
        };
        monitor.start();

        Map<String, Integer> entity2Freq = new HashMap<>();

        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            Mention2EntityInfoLine infoLine = Mention2EntityInfoLine.fromLine(line);
            for (Triple<String, Integer, Double> o : infoLine.entityFreq) {
                entity2Freq.put(o.first, entity2Freq.getOrDefault(o.first, 0) + o.second);
            }
            cnt.incrementAndGet();
        }
        monitor.forceShutdown();
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        entity2Freq.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue())).forEach(o -> {
            out.println(String.format("%s\t%d", o.getKey(), o.getValue()));
        });

        out.close();
    }

    public static void main(String[] args) {
//        process_0(args);
//        process_1(args);
        countEntity(args);
    }
}
