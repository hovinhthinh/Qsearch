package storage.table;

import com.google.gson.Gson;
import config.Configuration;
import data.tablem.TABLEM;
import model.table.Table;
import org.json.JSONException;
import org.json.JSONObject;
import util.Concurrent;
import util.FileUtils;
import util.HTTPRequest;
import util.SelfMonitor;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

public class ElasticSearchTableImport {
    public static final String PROTOCOL = Configuration.get("storage.elasticsearch.protocol");
    public static final String ES_HOST = Configuration.get("storage.elasticsearch.address");
    public static final String TABLE_INDEX = Configuration.get("storage.elasticsearch.table_index");
    public static final String TABLE_TYPE = Configuration.get("storage.elasticsearch.table_type");
    public static final int BATCH_SIZE = 1024 * 8;
    public static ArrayList<String> bulks = new ArrayList<>();

    static class TableIndex {
        private static final Gson GSON = new Gson();
        Table table;
        String pageContent, caption, pageTitle, tableText;

        public String toESBulkContent() {
            return new JSONObject()
                    .put("pageContent", pageContent)
                    .put("caption", caption)
                    .put("pageTitle", pageTitle)
                    .put("tableText", tableText)
                    .put("json", GSON.toJson(table))
                    .toString();
        }
    }

    public static String deleteIndex() {
        return HTTPRequest.DELETE(PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX, null);
    }

    public static String createIndex() {
        String body = "{\n" +
                "  \"mappings\": {\n" +
                "    \"" + TABLE_TYPE + "\": {\n" +
                "      \"properties\": {\n" +
                "        \"json\": {\n" + // this is in the model.table.Table format.
                "          \"index\": false,\n" +
                "          \"type\": \"text\"\n" +
                "        },\n" +
                "        \"pageContent\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"analyzer\": \"english\"\n" +
                "        },\n" +
                "        \"caption\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"analyzer\": \"english\"\n" +
                "        },\n" +
                "        \"pageTitle\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"analyzer\": \"english\"\n" +
                "        },\n" +
                "        \"tableText\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"analyzer\": \"english\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
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

    private static boolean bulk(TableIndex tableIndex) {
        try {
            JSONObject index = new JSONObject().put("index", new JSONObject().put("_id", tableIndex.table._id));
            String body = tableIndex.toESBulkContent();
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

        Gson gson = new Gson();
        for (String line : FileUtils.getLineStream(input)) {
            TableIndex tableIndex = gson.fromJson(line, TableIndex.class);
            if (tableIndex.table._id == null) {
                throw new RuntimeException("null table id");
            }
            if (!bulk(tableIndex)) {
                monitor.forceShutdown();
                return false;
            }
            monitor.incAndGet();
        }
        monitor.forceShutdown();
        if (bulks.size() > 0) {
            return callBulk();
        }
        return true;
    }

    // /GW/D5data-11/hvthinh/TABLEM/all/all+id.shuf.gz /GW/D5data-11/hvthinh/TABLEM/all/all+id.shuf_to_be_indexed.gz
    // Output content of canonicalized TableM for indexing into Elasticsearch.
    public static void processTableData(String[] args) {
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        Gson gson = new Gson();

        for (String line : stream) {
            try {
                JSONObject o = new JSONObject(line);
                TableIndex tableIndex = new TableIndex();
                tableIndex.table = TABLEM.parseFromJSON(o);
                if (tableIndex.table == null) {
                    continue;
                }
                tableIndex.caption = o.has("title") ? o.getString("title") : "";
                tableIndex.pageTitle = o.has("pageTitle") ? o.getString("pageTitle") : "";
                tableIndex.pageContent = o.has("plainTextContent") ? o.getString("plainTextContent") : "";
                tableIndex.tableText = o.has("table_as_text") ? o.getString("table_as_text") : "";

                out.println(gson.toJson(tableIndex));
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }
        }
        out.close();
    }

    private static String removeSearchable() {
        String body = "{\n" +
                "    \"script\" : \"ctx._source.remove('searchable')\",\n" +
                "    \"query\" : {\n" +
                "        \"exists\": { \"field\": \"searchable\" }\n" +
                "    }\n" +
                "}";
        return HTTPRequest.POST(PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX + "/_update_by_query?conflicts=proceed", body);
    }

    private static String setSearchable(String tableID) {
        String body = "{\"doc\":{\"searchable\":\"yes\"}}";
        try {
            return HTTPRequest.POST(PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX + "/" + TABLE_TYPE + "/" + URLEncoder.encode(tableID, "UTF-8") + "/_update", body);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean setSearchableDocuments(String input) {
        try {
            Concurrent.BoundedExecutor executor = new Concurrent.BoundedExecutor(96);
            SelfMonitor m = new SelfMonitor("SetSearchableDocs", -1, 10);
            m.start();
            Gson gson = new Gson();
            for (String line : FileUtils.getLineStream(input, "UTF-8")) {
                m.incAndGet();
                Table table = gson.fromJson(line, Table.class);
                executor.submit(() -> {
                    String output = setSearchable(table._id);
                    if (output == null) {
                        System.out.println("Err: " + table._id);
                    }
                    return null;
                });
            }
            m.forceShutdown();
            executor.joinAndShutdown(10);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }


    public static void main(String[] args) throws Exception {
//        processTableData(args);
//        System.out.println(deleteIndex());
//        System.out.println(createIndex());
//        System.out.println("Importing tables for TABLEM:");
//        System.out.println(importTables("/GW/D5data-11/hvthinh/TABLEM/all/all+id.shuf.to_be_indexed.gz"));
        System.out.println(removeSearchable());
        System.out.println(setSearchableDocuments("/GW/D5data-11/hvthinh/TABLEM/all/all+id.shuf.annotation.gz"));
    }
}
