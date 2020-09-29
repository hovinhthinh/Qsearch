package misc;

import server.common.handler.WikiViewHandler;
import util.Concurrent;
import util.FileUtils;
import util.SelfMonitor;
import yago.TaxonomyGraph;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WikipediaView {
    public static final File ENTITY_VIEW_FILE = new File("./resources/entityView-2019.tsv");
    public static HashMap<String, Integer> e2V = null;

    public static synchronized void load() {
        if (e2V != null) {
            return;
        }
        e2V = new HashMap<>();
        if (ENTITY_VIEW_FILE.exists()) {
            for (String line : FileUtils.getLineStream(ENTITY_VIEW_FILE, StandardCharsets.UTF_8)) {
                String[] arr = line.split("\t");
                e2V.put(arr[0], Integer.parseInt(arr[1]));
            }
        }
    }

    public static int getView(String entity) {
        if (e2V == null) {
            load();
        }
        return e2V.getOrDefault(entity, -1);
    }

    public static void main(String[] args) {
        TaxonomyGraph graph = TaxonomyGraph.getDefaultGraphInstance();

        AtomicInteger count = new AtomicInteger(0),
                countOut = new AtomicInteger(0),
                countErr = new AtomicInteger(0),
                countForeLang = new AtomicInteger(0);

        load();
        PrintWriter out = FileUtils.getPrintWriter(ENTITY_VIEW_FILE, StandardCharsets.UTF_8);
        for (Map.Entry<String, Integer> e : e2V.entrySet()) {
            out.println(e.getKey() + "\t" + e.getValue());
            countOut.incrementAndGet();
        }
        out.flush();

        SelfMonitor m = new SelfMonitor("ExtractEntityView-2019", graph.nEntities, 10) {
            @Override
            public void logProgress(Progress progress) {
                super.logProgress(progress);
                System.out.println("Good: " + countOut.get()
                        + "    Bad: " + countErr.get()
                        + "    ForeLang: " + countForeLang.get());
            }
        };
        m.start();

        m.incAndGet(null, countOut.get());

        Concurrent.runAndWait(() -> {
            do {
                int id = count.getAndIncrement();
                if (id >= graph.nEntities) {
                    break;
                }

                String entity = graph.id2Entity.get(id);
                if (e2V.containsKey(entity)) {
                    continue;
                }

                m.incAndGet();

                if (!entity.replaceFirst("^<[a-z]{2}/", "").equals(entity)) {
                    countForeLang.incrementAndGet();
                    continue;
                }

                int v = WikiViewHandler.getView(entity);
                if (v != -1) {
                    countOut.incrementAndGet();
                    synchronized (out) {
                        out.println(entity + "\t" + v);
                        out.flush();
                    }
                } else {
                    countErr.incrementAndGet();
                }
            } while (true);
        }, 512);

        m.forceShutdown();
        out.close();
    }
}
