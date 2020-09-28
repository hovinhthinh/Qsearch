package misc;

import scalaz.Alpha;
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
    public static final HashMap<String, Integer> e2V = new HashMap<>();

    public static int getView() {
        return 0;
    }

    public static final void load() {
        if (ENTITY_VIEW_FILE.exists()) {
            for (String line : FileUtils.getLineStream(ENTITY_VIEW_FILE, StandardCharsets.UTF_8)) {
                String[] arr = line.split("\t");
                e2V.put(arr[0], Integer.parseInt(arr[1]));
            }
        }
    }

    public static void main(String[] args) {
        TaxonomyGraph graph = TaxonomyGraph.getDefaultGraphInstance();

        load();
        PrintWriter out = FileUtils.getPrintWriter(ENTITY_VIEW_FILE, StandardCharsets.UTF_8);
        for (Map.Entry<String, Integer> e : e2V.entrySet()) {
            out.println(e.getKey() + "\t" + e.getValue());
        }
        out.flush();

        AtomicInteger count = new AtomicInteger(0);

        AtomicInteger countOut = new AtomicInteger(0),
                countErr = new AtomicInteger(0),
                countIgnore = new AtomicInteger(0),
                countForeLang = new AtomicInteger(0);

        SelfMonitor m = new SelfMonitor("ExtractEntityView-2019", graph.nEntities, 10) {
            @Override
            public void logProgress(Progress progress) {
                super.logProgress(progress);
                System.out.println("Good: " + countOut.get()
                        + "    Bad: " + countErr.get()
                        + "    ForeLang: " + countForeLang.get()
                        + "    Ignore: " + countIgnore.get());
            }
        };
        m.start();

        Concurrent.runAndWait(() -> {
            do {
                int id = count.getAndIncrement();
                if (id >= graph.nEntities) {
                    break;
                }
                m.incAndGet();
                String entity = graph.id2Entity.get(id);

                if (e2V.containsKey(entity)) {
                    countOut.incrementAndGet();
                    continue;
                }

                if (!entity.replaceFirst("^<[a-z]{2}/", "").equals(entity)) {
                    countForeLang.incrementAndGet();
                    continue;
                }

                boolean flag = false;
                for (int i = 0; i < entity.length(); ++i) {
                    if (Character.isLetterOrDigit(entity.charAt(i))) {
                        flag = true;
                        break;
                    }
                }

                if (!flag) {
                    countIgnore.incrementAndGet();
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
                    System.err.println("Err: " + entity);
                }
            } while (true);
        }, 32);

        m.forceShutdown();
        out.close();
    }
}
