package storage.table;

import com.google.gson.Gson;
import config.Configuration;
import model.table.Table;
import org.json.JSONException;
import org.json.JSONObject;
import storage.table.index.TableIndex;
import util.Concurrent;
import util.FileUtils;
import util.HTTPRequest;
import util.SelfMonitor;

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

    public static String deleteIndex() {
        return HTTPRequest.DELETE(PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX, null);
    }

    public static String createIndex() {
        String body = "{\n" +
                "  \"mappings\": {\n" +
                "    \"" + TABLE_TYPE + "\": {\n" +
                "      \"properties\": {\n" +
                "        \"json\": {\n" +
                "          \"index\": false,\n" +
                "          \"type\": \"text\"\n" +
                "        },\n" +
                "        \"parsedJson\": {\n" +
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
                "        },\n" +
                "        \"searchable\": {\n" +
                "          \"type\": \"text\"\n" +
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

    // Output content of canonicalized tables for indexing into Elasticsearch.
    // TableM input: /GW/D5data-11/hvthinh/TABLEM/all/all+id.gz
    // Wikipedia input: /GW/D5data-12/hvthinh/wikipedia_dump/enwiki-20200301-pages-articles-multistream.xml.bz2.tables+id.gz
    public static void processTableData() {
        throw new RuntimeException("Moved to package 'index'");
    }

    private static String removeField(String field) {
        String body = "{\n" +
                "    \"script\" : \"ctx._source.remove('" + field + "')\",\n" +
                "    \"query\" : {\n" +
                "        \"exists\": { \"field\": \"" + field + "\" }\n" +
                "    }\n" +
                "}";
        return HTTPRequest.POST(PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX + "/_update_by_query?conflicts=proceed", body);
    }

    private static String setSearchable(String tableID, String parsedJson) {
        String body = new JSONObject().put("doc", new JSONObject().put("searchable", "yes").put("parsedJson", parsedJson)).toString();
        try {
            return HTTPRequest.POST(PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX + "/" + TABLE_TYPE + "/" + URLEncoder.encode(tableID, "UTF-8") + "/_update", body);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean setSearchableDocuments(String inputFile) {
        try {
            Concurrent.BoundedExecutor executor = new Concurrent.BoundedExecutor(64);
            SelfMonitor m = new SelfMonitor("SetSearchableDocs", -1, 10);
            m.start();
            Gson gson = new Gson();
            for (String line : FileUtils.getLineStream(inputFile, "UTF-8")) {
                m.incAndGet();
                Table table = gson.fromJson(line, Table.class);
                executor.submit(() -> {
                    String output = setSearchable(table._id, line);
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
            return false;
        }
        return true;
    }


    public static void main(String[] args) throws Exception {
//        processTableData();
//        System.out.println(deleteIndex());
//        System.out.println(createIndex());
//        System.out.println(importTables("/GW/D5data-12/hvthinh/TabQs/to_be_indexed/wiki.gz"));
//        System.out.println(importTables("/GW/D5data-12/hvthinh/TabQs/to_be_indexed/tablem.gz"));

        // Optional
//        System.out.println("Remove 'searchable' Field: " + removeField("searchable"));
//        System.out.println("Remove 'parsedJson' Field: " + removeField("parsedJson"));

//        System.out.println(setSearchableDocuments("/GW/D5data-11/hvthinh/TABLEM/all/all+id.annotation+linking.gz"));
//        System.out.println(setSearchableDocuments("/GW/D5data-12/hvthinh/wikipedia_dump/enwiki-20200301-pages-articles-multistream.xml.bz2.tables+id_annotation+linking.gz"));
    }
}
