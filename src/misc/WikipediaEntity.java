package misc;

import config.Configuration;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.FileUtils;
import util.HTTPRequest;
import util.SelfMonitor;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;

public class WikipediaEntity {
    public static final String PROTOCOL = Configuration.get("storage.elasticsearch.protocol");
    public static final String ES_HOST = Configuration.get("storage.elasticsearch.address");
    public static final String WIKIPEDIA_INDEX = "wikipedia";
    public static final String ENTITY_TYPE = Configuration.get("storage.elasticsearch.entity_type");
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

            JSONObject index = new JSONObject().put("index", new JSONObject().put("_id", entity));
            String body = new JSONObject().put("pageContent", content).put("entityList", entities).toString();
            bulks.add(index.toString());
            bulks.add(body);
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
    public static String getContentOfEntity(String entity) {
        try {
            String content = HTTPRequest.GET(PROTOCOL + "://" + ES_HOST + "/" + WIKIPEDIA_INDEX + "/" + ENTITY_TYPE + "/" + URLEncoder.encode(entity, "UTF-8"));
            if (content == null) {
                return null;
            }
            return new JSONObject(content).getJSONObject("_source").getString("pageContent");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Integer getCoocurrencePageCountOfEntities(String entityA, String entityB) {
        try {
            String body = new JSONObject().put("query", new JSONObject().put("bool", new JSONObject().put("must", new JSONArray()
                    .put(new JSONObject().put("match", new JSONObject().put("entityList", entityA)))
                    .put(new JSONObject().put("match", new JSONObject().put("entityList", entityB)))
            ))).toString();

            String content = HTTPRequest.POST(
                    PROTOCOL + "://" + ES_HOST + "/" + WIKIPEDIA_INDEX + "/" + ENTITY_TYPE + "/_search/?filter_path=hits.total",
                    body);
            if (content == null) {
                return null;
            }

            return new JSONObject(content).getJSONObject("hits").getInt("total");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
//        System.out.println(deleteIndex());
//        System.out.println(createIndex());
//        System.out.println(importTables("/GW/D5data-11/hvthinh/WIKIPEDIA-niko/fixedWikipediaEntitiesJSON.gz"));
//        System.out.println(getContentOfEntityPage("<Cristiano_Ronaldo>"));
        System.out.println(getCoocurrencePageCountOfEntities("<Cristiano_Ronaldo>", "<Portugal>"));
    }
}
