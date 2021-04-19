package storage.text.migrate;

import config.Configuration;
import model.text.QuantitativeFact;
import model.text.Sentence;
import model.text.Token;
import model.text.tag.EntityTag;
import model.text.tag.TimeTag;
import net.openhft.chronicle.map.ChronicleMap;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import storage.text.ElasticSearchDataImport;
import util.*;
import yago.TaxonomyGraph;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class ChronicleMapQfactStorage {
    public static final String QFACT_INDEX_FILE = Configuration.get("chroniclemap.text.index_file");

    private static ChronicleMap<String, byte[]> INDEX = null;

    public static ArrayList<String> SEARCHABLE_ENTITIES = null;

    static {
        try {
            INDEX = ChronicleMap
                    .of(String.class, byte[].class)
                    .averageKeySize(30)
                    .averageValueSize(1500)
                    .actualSegments(32)
                    .entriesPerSegment(60000)
                    .createPersistedTo(new File(QFACT_INDEX_FILE));
            SEARCHABLE_ENTITIES = new ArrayList<>();
            INDEX.forEachEntry(e -> {
                SEARCHABLE_ENTITIES.add(e.key().get());
            });
            SEARCHABLE_ENTITIES.trimToSize();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void migrateElasticSearchStorageToChronicleMap() {
        INDEX.clear();
        try {
            String url = ElasticSearchDataImport.PROTOCOL + "://" + ElasticSearchDataImport.ES_HOST + "/" + ElasticSearchDataImport.INDEX + "/" + ElasticSearchDataImport.TYPE + "/_search?scroll=15m";
            String body = "{\"size\":1000,\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"searchable\"}}]}}}";
            String data = HTTPRequest.POST(url, body);
            if (data == null) {
                throw new RuntimeException("file not found.");
            }
            JSONObject json = new JSONObject(data);
            String scroll_id = json.getString("_scroll_id");

            json = json.getJSONObject("hits");
            SelfMonitor m = new SelfMonitor(null, json.getInt("total"), 10);
            m.start();

            JSONArray arr = json.getJSONArray("hits");
            for (int i = 0; i < arr.length(); ++i) {
                JSONObject entityFacts = arr.getJSONObject(i);
                String entity = entityFacts.getString("_id");
                JSONArray facts = entityFacts.getJSONObject("_source").getJSONArray("facts");
                String value = facts.toString();
                INDEX.put(entity, ObjectCompressor.compressStringIntoByteArray(value));
                m.incAndGet();
            }

            url = ElasticSearchDataImport.PROTOCOL + "://" + ElasticSearchDataImport.ES_HOST + "/_search/scroll";
            body = "{\"scroll\":\"15m\",\"scroll_id\":\"" + scroll_id + "\"}";
            do {
                data = HTTPRequest.POST(url, body);
                if (data == null) {
                    throw new RuntimeException("scroll_id expired.");
                }
                json = new JSONObject(data);
                arr = json.getJSONObject("hits").getJSONArray("hits");
                for (int i = 0; i < arr.length(); ++i) {
                    JSONObject entityFacts = arr.getJSONObject(i);
                    String entity = entityFacts.getString("_id");
                    JSONArray facts = entityFacts.getJSONObject("_source").getJSONArray("facts");
                    String value = facts.toString();
                    INDEX.put(entity, ObjectCompressor.compressStringIntoByteArray(value));
                    m.incAndGet();
                }
            } while (arr.length() > 0);

            m.forceShutdown();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("unknown exception.");
        }
        INDEX.close();
    }

    public static JSONArray get(String entity) {
        return new JSONArray(ObjectCompressor.decompressByteArrayIntoString(INDEX.get(entity)));
    }

    public static boolean importFacts(String input, double qfactMinConf, double entityMinConf) {
        SelfMonitor m = new SelfMonitor("importFacts", -1, 10);

        try {
            // Load
            File tempFile = File.createTempFile("yagoImport", ".gz", new File("/tmp/"));
            System.out.println("Loading: " + tempFile.getAbsolutePath());
            PrintWriter tempOut = FileUtils.getPrintWriter(tempFile, Charset.forName("UTF-8"));
            for (String line : FileUtils.getLineStream(input, "UTF-8")) {
                Sentence sent = Gson.fromJson(line, Sentence.class);
                for (QuantitativeFact qfact : sent.quantitativeFacts) {
                    if (qfact.conf <= Constants.EPS) {
                        qfact.conf = Math.exp(qfact.conf);
                    }
                    if (qfact.entityTag == null || qfact.negated || qfact.entityTag.confidence < entityMinConf || qfact.conf < qfactMinConf) {
                        continue;
                    }

                    String entity = null;
                    if (qfact.entityTag.id.startsWith("YAGO3:")) { // STICS
                        entity = StringEscapeUtils.unescapeJava(qfact.entityTag.id.substring(6));
                    } else if (qfact.entityTag.id.startsWith("YAGO:")) { // NYT
                        entity = "<" + StringEscapeUtils.unescapeJava(qfact.entityTag.id.substring(5)) + ">";
                    } else if (qfact.entityTag.id.startsWith("<") && qfact.entityTag.id.endsWith(">")) { // WIKI
                        entity = "<" + StringEscapeUtils.unescapeJava(qfact.entityTag.id.substring(1, qfact.entityTag.id.length() - 1)) + ">";
                    } else {
                        throw new RuntimeException("Entity unrecognized");
                    }
                    JSONObject data = new JSONObject();
                    data.put("qfact_conf", qfact.conf);
                    data.put("entity_conf", qfact.entityTag.confidence);
                    data.put("quantity", qfact.quantityTag.quantity.toString());
                    data.put("sentence", sent.toString());
                    data.put("source", sent.source == null ? "null" : sent.source);
                    data.put("entityStr", sent.getSubTokensString(qfact.entityTag.beginIndex, qfact.entityTag.endIndex));
                    data.put("quantityStr", sent.getSubTokensString(qfact.quantityTag.beginIndex, qfact.quantityTag.endIndex));
                    JSONArray context = new JSONArray();
                    // Build context
                    for (EntityTag t : qfact.contextEntityTags) {
//                        context.put(t.id); // Not using entityID at this time.
                        context.put(String.format("<E>:%s\t%s", sent.getSubTokensString(t.beginIndex, t.endIndex), t.id));
                    }
                    for (TimeTag t : qfact.contextTimeTags) {
                        // Not using time range at this time. (we may ignore time in the context when processing
                        // matching).
                        context.put(String.format("<T>:%s\t%d\t%d", sent.getSubTokensString(t.beginIndex, t.endIndex), t.rangeFrom, t.rangeTo));
                    }
                    for (Token t : qfact.contextTokens) {
                        context.put(t.str);
                    }
                    data.put("context", context);
                    tempOut.println(String.format("%s\t%s", entity, data.toString()));
                }
            }
            tempOut.close();

            File newTempFile = File.createTempFile("yagoImport", ".gz", new File("/tmp/"));
            System.out.println("Preparing: " + newTempFile.getAbsolutePath());
            ShellCommand.execute("zcat " + tempFile.getAbsolutePath() + " | LC_ALL=C sort | gzip > " + newTempFile.getAbsolutePath());
            tempFile.delete();

            // Import.
            System.out.println("Importing facts");
            INDEX.clear();
            m.start();
            String lastEntity = null;
            JSONArray entityFacts = null;
            for (String line : FileUtils.getLineStream(newTempFile, Charset.forName("UTF-8"))) {
                int pos = line.indexOf("\t");
                String entity = line.substring(0, pos);
                JSONObject data = new JSONObject(line.substring(pos + 1));
                if (lastEntity != null && entity.equals(lastEntity)) {
                    entityFacts.put(data);
                } else {
                    if (lastEntity != null && TaxonomyGraph.getDefaultGraphInstance().entity2Id.containsKey(lastEntity)) {
                        INDEX.put(lastEntity, ObjectCompressor.compressStringIntoByteArray(entityFacts.toString()));
                    }
                    lastEntity = entity;
                    entityFacts = new JSONArray().put(data);
                }
                m.incAndGet();
            }
            if (lastEntity != null && TaxonomyGraph.getDefaultGraphInstance().entity2Id.containsKey(lastEntity)) {
                INDEX.put(lastEntity, ObjectCompressor.compressStringIntoByteArray(entityFacts.toString()));
            }
            newTempFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            m.forceShutdown();
        }

        System.out.println("Importing facts succeeded. Total entities: " + INDEX.size());
        return true;
    }

    public static void main(String[] args) {
//        migrateElasticSearchStorageToChronicleMap();
//        System.out.println(importFacts("./data/stics+nyt/full_all.gz", 0.7, 0));
    }
}
