package misc;

import config.Configuration;

import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONException;
import org.json.JSONObject;
import util.FileUtils;
import util.HTTPRequest;
import util.SelfMonitor;

import java.util.ArrayList;

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

    private static int nIgnoredDocs = 0;

    private static boolean bulk(JSONObject o) {
        try {
            String content = o.getString("content");
            String entity = StringEscapeUtils.unescapeJava("<" + content.substring(0, content.indexOf("\n")).trim().replaceAll(" ", "_") + ">");
            if (entity.length() > 80) {
                System.out.println("Ignore invalid entity: " + entity);
                ++nIgnoredDocs;
                return true;
            }
            JSONObject index = new JSONObject().put("index", new JSONObject().put("_id", entity));
            String body = new JSONObject().put("pageContent", content).toString();
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
        System.out.println("#IgnoredDocs: " + nIgnoredDocs);
        return true;
    }


    public static void main(String[] args) throws Exception {
        System.out.println(deleteIndex());
        System.out.println(createIndex());
        System.out.println(importTables("/GW/D5data-11/hvthinh/WIKIPEDIA-niko/fixedWikipediaEntitiesJSON.gz"));
    }
}
