package storage.table;

import com.google.gson.Gson;
import config.Configuration;
import model.table.Table;
import org.json.JSONException;
import org.json.JSONObject;
import util.FileUtils;
import util.HTTPRequest;
import util.Monitor;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ElasticSearchTableImport {
    public static final String PROTOCOL = Configuration.get("storage.elasticsearch.protocol");
    public static final String ES_HOST = Configuration.get("storage.elasticsearch.address");
    public static final String TABLE_INDEX = Configuration.get("storage.elasticsearch.table_index");
    public static final String TABLE_TYPE = Configuration.get("storage.elasticsearch.table_type");
    public static final int BATCH_SIZE = 1024 * 8;
    public static ArrayList<String> bulks = new ArrayList<>();

    public static String deleteIndex() {
        return HTTPRequest.DELETE(PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX, null);
    }

    public static String createIndex() {
        String body = "{\"mappings\":{\"" + TABLE_TYPE + "\":{\"properties\":{}}}}";
        return HTTPRequest.PUT(PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX, body);
    }

    private static boolean callBulk() {
        StringBuilder sb = new StringBuilder();
        for (String s : bulks) {
            sb.append(s).append("\n");
        }

        String response = HTTPRequest.POST(PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX + "/" + TABLE_TYPE + "/_bulk",
                sb.toString());

        if (response != null) {
            bulks.clear();
            return true;
        } else {
            return false;
        }
    }

    private static boolean bulk(String tid, String json) {
        try {
            JSONObject index = new JSONObject().put("index", new JSONObject().put("_id", tid));
            JSONObject body = new JSONObject().put("json", json);
            bulks.add(index.toString());
            bulks.add(body.toString());
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
    public static boolean importTables(String input) {
        System.out.println("Import tables from: " + input);

        AtomicInteger processed = new AtomicInteger(0);

        Monitor monitor = new Monitor("ImportTable", -1, 10) {
            @Override
            public int getCurrent() {
                return processed.get();
            }
        };
        monitor.start();

        Gson gson = new Gson();
        for (String line : FileUtils.getLineStream(input)) {
            Table t = gson.fromJson(line, Table.class);
            String id = t._id;
            if (id == null) {
                throw new RuntimeException("null table id");
            }
            if (!bulk(id, line)) {
                monitor.forceShutdown();
                return false;
            }
            processed.incrementAndGet();
        }
        monitor.forceShutdown();
        if (bulks.size() > 0) {
            return callBulk();
        }
        return true;
    }


    public static void main(String[] args) throws Exception {
        System.out.println(deleteIndex());
        System.out.println(createIndex());
        System.out.println("Importing tables:");
        System.out.println(importTables(null));
    }

}
