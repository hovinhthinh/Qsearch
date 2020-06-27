package misc;

import config.Configuration;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import nlp.NLP;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.ac.susx.informatics.Morpha;
import util.FileUtils;
import util.HTTPRequest;
import util.SelfMonitor;
import util.headword.StringUtils;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class WikipediaEntity {
    public static final String PROTOCOL = Configuration.get("storage.elasticsearch.protocol");
    public static final String ES_HOST = Configuration.get("storage.elasticsearch.address");
    public static final String WIKIPEDIA_INDEX = "wikipedia";
    public static final String ENTITY_TYPE = "entity";
    public static final int BATCH_SIZE = 1024 * 4;
    public static ArrayList<String> bulks = new ArrayList<>();

    public static String deleteIndex() {
        return HTTPRequest.DELETE(PROTOCOL + "://" + ES_HOST + "/" + WIKIPEDIA_INDEX, null);
    }

    public static String createIndex() {
        String body = "{\n" +
                "  \"mappings\": {\n" +
                "    \"" + ENTITY_TYPE + "\": {\n" +
                "      \"properties\": {\n" +
                "        \"pageContent\": {\n" +
                "          \"index\": false,\n" +
                "          \"type\": \"text\"\n" +
                "        },\n" +
                "        \"entityList\": {\n" +
                "          \"index\": true,\n" +
                "          \"type\": \"keyword\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        return HTTPRequest.PUT(PROTOCOL + "://" + ES_HOST + "/" + WIKIPEDIA_INDEX, body);
    }

    private static boolean callBulk() {
        StringBuilder sb = new StringBuilder();
        int size = 0;
        for (String s : bulks) {
            sb.append(s).append("\n");
            size += s.length() + 1;
        }

        String response = HTTPRequest.POST(PROTOCOL + "://" + ES_HOST + "/" + WIKIPEDIA_INDEX + "/" + ENTITY_TYPE + "/_bulk",
                sb.toString());
        System.out.println("CallBulk_Size: " + size + "B");
        if (response != null) {
            bulks.clear();
            return true;
        } else {
            return false;
        }
    }

    private static int nNonEntityPages = 0;

    private static boolean bulk(JSONObject o) {
        try {
            JSONObject index = new JSONObject().put("index", new JSONObject().put("_id", o.getString("_id")));
            o.remove("_id");
            bulks.add(index.toString());
            bulks.add(o.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
        if (bulks.size() >= BATCH_SIZE * 2) {
            return callBulk();
        }
        return true;
    }

    // args: <table file>
    private static boolean importTables(String input) {
        System.out.println("Import tables from: " + input);

        SelfMonitor monitor = new SelfMonitor("ImportTable", -1, 10);
        monitor.start();

        for (String line : FileUtils.getLineStream(input)) {
            JSONObject o = new JSONObject(line);
            if (!bulk(o)) {
                monitor.forceShutdown();
                return false;
            }
            monitor.incAndGet();
        }
        monitor.forceShutdown();
        if (bulks.size() > 0) {
            return callBulk();
        }
        System.out.println("#Non-EntityPages: " + nNonEntityPages);
        return true;
    }


    // e.g., entity: <Ronaldo>
    // return empty if not found, null on error
    public static String getContentOfEntityPage(String entity) {
        String content = null;
        try {
            content = HTTPRequest.GET(PROTOCOL + "://" + ES_HOST + "/" + WIKIPEDIA_INDEX + "/" + ENTITY_TYPE + "/"
                            + URLEncoder.encode(entity, "UTF-8"),
                    true);
            if (content == null) {
                System.err.println("Cannot connect to Elasticsearch");
                return null;
            }
            JSONObject obj = new JSONObject(content);
            if (obj.has("found")) {
                return obj.getBoolean("found")
                        ? obj.getJSONObject("_source").getString("pageContent")
                        : "";
            } else {
                return null;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        } catch (JSONException e) {
            System.err.println("Elasticsearch error [getContentOfEntityPage]: " + entity + "\t" + content);
            System.err.flush();
            return null;
        }
    }

    public static HashSet<String> getTermSetOfEntityPage(String entity) {
        String content = null;
        try {
            content = HTTPRequest.GET(PROTOCOL + "://" + ES_HOST + "/" + WIKIPEDIA_INDEX + "/" + ENTITY_TYPE + "/"
                            + URLEncoder.encode(entity, "UTF-8"),
                    true);
            if (content == null) {
                System.err.println("Cannot connect to Elasticsearch");
                return null;
            }
            JSONObject obj = new JSONObject(content);
            if (obj.has("found")) {
                return obj.getBoolean("found")
                        ? new HashSet<>(Arrays.asList(obj.getJSONObject("_source").getString("termSet").split(" ")))
                        : new HashSet<>();
            } else {
                return null;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        } catch (JSONException e) {
            System.err.println("Elasticsearch error [getTermSetOfEntityPage]: " + entity + "\t" + content);
            System.err.flush();
            return null;
        }
    }

    private static final Object2IntLinkedOpenHashMap<String> COOCCURENCE_CACHE = new Object2IntLinkedOpenHashMap<>();
    private static final int COOCCURENCE_CACHE_SIZE = 100000;

    static {
        COOCCURENCE_CACHE.defaultReturnValue(-1);
    }

    // return null on error
    public static Integer getCoocurrencePageCountOfEntities(String entityA, String entityB) {
        String key = entityA.compareTo(entityB) < 0 ? String.format("%s\t%s", entityA, entityB) : String.format("%s\t%s", entityB, entityA);
        int result = COOCCURENCE_CACHE.getAndMoveToFirst(key);
        if (result != -1) {
            return result;
        }

        String content = null;
        try {
            String body = new JSONObject().put("query", new JSONObject().put("bool", new JSONObject().put("must", new JSONArray()
                    .put(new JSONObject().put("match", new JSONObject().put("entityList", entityA)))
                    .put(new JSONObject().put("match", new JSONObject().put("entityList", entityB)))
            ))).toString();

            content = HTTPRequest.POST(
                    PROTOCOL + "://" + ES_HOST + "/" + WIKIPEDIA_INDEX + "/" + ENTITY_TYPE + "/_search/?filter_path=hits.total",
                    body, true);
            if (content == null) {
                System.err.println("Cannot connect to Elasticsearch");
                return null;
            }

            result = new JSONObject(content).getJSONObject("hits").getInt("total");
            COOCCURENCE_CACHE.putAndMoveToFirst(key, result);
            if (COOCCURENCE_CACHE.size() > COOCCURENCE_CACHE_SIZE) {
                COOCCURENCE_CACHE.removeLastInt();
            }
            return result;
        } catch (JSONException e) {
            System.err.println("Elasticsearch error [getCoocurrencePageCountOfEntities]: " + key + "\t" + content);
            System.err.flush();
            return null;
        }
    }


    public static void preprocess(String[] args) {
        // PROCESS PARALLEL
        // args: /GW/D5data-11/hvthinh/WIKIPEDIA-niko/fixedWikipediaEntitiesJSON.gz /GW/D5data-11/hvthinh/WIKIPEDIA-niko/fixedWikipediaEntitiesJSON_withTermSet.gz
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            try {
                JSONObject o = new JSONObject(line);
                String content = o.getString("content");
                String entity = StringEscapeUtils.unescapeJava("<" + content.substring(0, content.indexOf("\n")).trim().replaceAll(" ", "_") + ">");

                HashSet<String> entitySet = new HashSet<>();
                if (entity.length() > 80) {
                    ++nNonEntityPages;
                    // This is not an entity page, we use the id of page.
                    String url = o.getString("source");
                    entity = "__curid_" + url.substring(url.lastIndexOf("=") + 1);
                    System.out.println("Non-EntityPage: " + entity);
                } else {
                    entitySet.add(entity);
                }

                for (String id : o.getJSONObject("entities").keySet()) {
                    entitySet.add(id);
                }

                JSONArray entities = new JSONArray();
                for (String e : entitySet) {
                    entities.put(e);
                }

                // term set
                HashSet<String> termSet = new HashSet<>();
                for (String t : NLP.tokenize(content)) {
                    termSet.add(StringUtils.stem(t.toLowerCase(), Morpha.any));
                }

                JSONObject data = new JSONObject()
                        .put("_id", entity)
                        .put("pageContent", content)
                        .put("entityList", entities)
                        .put("termSet", String.join(" ", termSet));

                out.println(data.toString());
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }
        }
        out.close();
        // END PARALLEL PART
    }

    public static void main(String[] args) throws Exception {
//        preprocess(args);

//        System.out.println(deleteIndex());
//        System.out.println(createIndex());
//        System.out.println(importTables("/GW/D5data-11/hvthinh/WIKIPEDIA-niko/fixedWikipediaEntitiesJSON_withTermSet.gz"));
//        System.out.println(getContentOfEntityPage("<Cristiano_Ronaldo>"));
//        System.out.println(getCoocurrencePageCountOfEntities("<Cristiano_Ronaldo>", "<Portugal>"));
    }
}
