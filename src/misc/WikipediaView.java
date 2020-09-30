package misc;

import org.json.JSONArray;
import org.json.JSONObject;
import util.Concurrent;
import util.Crawler;
import util.FileUtils;
import util.SelfMonitor;
import yago.TaxonomyGraph;

import java.io.File;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WikipediaView {
    private static final String URL_TEMPLATE = "https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/en.wikipedia/all-access/all-agents/{ENTITY}/monthly/20190101/20191231";

    // returns: -2: article not found; -1: unknown error (e.g. network error, timeout, ...)
    public static int getViewFromOnlineAPI(String entity) {
        try {
            String url = URL_TEMPLATE.replace("{ENTITY}", URLEncoder.encode(entity.substring(1, entity.length() - 1), "UTF-8"));
            String content = Crawler.getContentFromUrl(url, null, "GET", null, null, true);

            JSONObject json = new JSONObject(content);
            if (json.has("title") && json.getString("title").equals("Not found.")) {
                return -2;
            }

            JSONArray monthly = json.getJSONArray("items");
            int v = 0;
            for (int i = 0; i < monthly.length(); ++i) {
                v += monthly.getJSONObject(i).getInt("views");
            }
            return v;
        } catch (Exception e) {
            return -1;
        }
    }

    public static final File ENTITY_VIEW_FILE = new File("./resources/entityView-2019.tsv");
    public static HashMap<String, Integer> e2V = null;

    public static synchronized void load(boolean loadNotFound) {
        if (e2V != null) {
            return;
        }
        e2V = new HashMap<>();
        if (ENTITY_VIEW_FILE.exists()) {
            for (String line : FileUtils.getLineStream(ENTITY_VIEW_FILE, StandardCharsets.UTF_8)) {
                String[] arr = line.split("\t");
                int v = Integer.parseInt(arr[1]);
                if (loadNotFound || v >= 0) {
                    e2V.put(arr[0], v);
                }
            }
        }
    }

    public static int getView(String entity) {
        if (e2V == null) {
            load(false);
        }
        return e2V.getOrDefault(entity, -1);
    }

    // Crawl view from online API.
    public static void main(String[] args) {
        TaxonomyGraph graph = TaxonomyGraph.getDefaultGraphInstance();

        AtomicInteger count = new AtomicInteger(0),
                countOut = new AtomicInteger(0),
                countErr = new AtomicInteger(0),
                countNotFound = new AtomicInteger(0),
                countForeLang = new AtomicInteger(0);

        load(true);
        PrintWriter out = FileUtils.getPrintWriter(ENTITY_VIEW_FILE, StandardCharsets.UTF_8);
        for (Map.Entry<String, Integer> e : e2V.entrySet()) {
            out.println(e.getKey() + "\t" + e.getValue());
            if (e.getValue() >= 0) {
                countOut.incrementAndGet();
            } else {
                countNotFound.incrementAndGet();
            }
        }
        out.flush();

        SelfMonitor m = new SelfMonitor("ExtractEntityView-2019", graph.nEntities, 10) {
            @Override
            public void logProgress(Progress progress) {
                super.logProgress(progress);
                System.out.println("Good: " + countOut.get()
                        + "    NotFound: " + countNotFound.get()
                        + "    Bad: " + countErr.get()
                        + "    ForeLang: " + countForeLang.get());
            }
        };
        m.start();

        m.incAndGet(null, countOut.get());
        m.incAndGet(null, countNotFound.get());

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

                int v = getViewFromOnlineAPI(entity);
                if (v != -1) {
                    if (v >= 0) {
                        countOut.incrementAndGet();
                    } else {
                        countNotFound.incrementAndGet();
                    }
                    synchronized (out) {
                        out.println(entity + "\t" + v);
                        out.flush();
                    }
                } else {
                    countErr.incrementAndGet();
                }
            } while (true);
        }, 16);

        m.forceShutdown();
        out.close();
    }
}
