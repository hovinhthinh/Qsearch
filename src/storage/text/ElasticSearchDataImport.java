package storage.text;

import config.Configuration;
import model.text.QuantitativeFact;
import model.text.Sentence;
import model.text.Token;
import model.text.tag.EntityTag;
import model.text.tag.TimeTag;
import nlp.NLP;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.ac.susx.informatics.Morpha;
import util.*;

import java.io.File;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Deprecated
public class ElasticSearchDataImport {
    public static final String PROTOCOL = Configuration.get("storage.elasticsearch.protocol");
    public static final String ES_HOST = Configuration.get("storage.elasticsearch.address");
    public static final String INDEX = Configuration.get("storage.elasticsearch.text_index");
    public static final String TYPE = Configuration.get("storage.elasticsearch.text_type");
    public static final int BATCH_SIZE = 1024 * 8;
    public static ArrayList<String> bulks = new ArrayList<>();
    static AtomicInteger updated = new AtomicInteger(0), threw = new AtomicInteger(0);

    public static String deleteIndex() {
        return HTTPRequest.DELETE(PROTOCOL + "://" + ES_HOST + "/" + INDEX, null);
    }

    public static String createIndex() {
        String body = "{\"mappings\":{\"" + TYPE + "\":{\"properties\":{\"types\":{\"type\":\"nested\"," +
                "\"enabled\":true},\"facts\":{\"type\":\"object\",\"enabled\":false}}}}}";
        return HTTPRequest.PUT(PROTOCOL + "://" + ES_HOST + "/" + INDEX, body);
    }

    private static boolean callBulk() {
        StringBuilder sb = new StringBuilder();
        for (String s : bulks) {
            sb.append(s).append("\n");
        }

        String response = HTTPRequest.POST(PROTOCOL + "://" + ES_HOST + "/" + INDEX + "/" + TYPE + "/_bulk",
                sb.toString());

        if (response != null) {
            bulks.clear();
            return true;
        } else {
            return false;
        }
    }

    private static boolean bulk(String entity, ArrayList<String> types) {
        try {
            JSONObject index = new JSONObject().put("index", new JSONObject().put("_id", entity));
            JSONObject body = new JSONObject().put("types", new JSONArray(types.stream().map(o -> {
                        try {
                            return new JSONObject().put("value", o);
                        } catch (JSONException ex) {
                            return null;
                        }
                    }
            ).collect(Collectors.toList())));

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

    public static boolean importYago(String input) {
        String e = null;
        ArrayList<String> ts = new ArrayList<>();
        for (String line : FileUtils.getLineStream(input)) {
            String[] arr = line.split("\t");
            if (arr.length != 4 || !arr[2].equals("rdf:type")) {
                continue;
            }
            String entity = StringEscapeUtils.unescapeJava(arr[1]);
            String type = NLP.stripSentence(arr[3].replaceAll("[^A-Za-z0-9]", " ")).toLowerCase();
            type = NLP.fastStemming(type, Morpha.noun);
            if (type.startsWith("wikicat ")) {
                type = type.substring(8);
            }
            if (type.startsWith("wordnet ")) {
                type = type.substring(type.indexOf(" ") + 1, type.lastIndexOf(" "));
            }
            if (e != null && !e.equals(entity)) {
                if (!bulk(e, ts)) {
                    return false;
                }
                ts.clear();
            }
            e = entity;
            ts.add(type);
        }
        if (ts.size() > 0 && !bulk(e, ts)) {
            return false;
        }
        if (bulks.size() > 0) {
            return callBulk();
        }
        return true;
    }

    private static void importEntityFacts(String entity, JSONArray entityFacts) throws Exception {
        String data =
                HTTPRequest.GET(PROTOCOL + "://" + ES_HOST + "/" + INDEX + "/" + TYPE + "/" + URLEncoder.encode(entity, "UTF-8"));
        if (data == null) {
            System.out.println("Threw: " + entity);
            threw.incrementAndGet();
            return;
        }
        System.out.println("Adding: " + entity);
        JSONObject newData = new JSONObject(data).getJSONObject("_source");
        // Append, not create new.
        JSONArray newFacts = newData.has("facts") ? newData.getJSONArray("facts") : new JSONArray();
        for (int i = 0; i < entityFacts.length(); ++i) {
            newFacts = newFacts.put(entityFacts.get(i));
        }
        newData.put("facts", newFacts);
        newData.put("searchable", "yes");
//        System.out.println(newData.toString());
        String response =
                HTTPRequest.PUT(PROTOCOL + "://" + ES_HOST + "/" + INDEX + "/" + TYPE + "/" + URLEncoder.encode(entity, "UTF-8"), newData.toString());
//        System.out.println(response);
        if (response == null) {
            throw new Exception("Importing facts fail.");
        }
        updated.incrementAndGet();
        System.out.println("Updated : " + updated.get() + " Threw: " + threw.get());
    }

    public static boolean importFacts(String input, double minConf) {
        try {
            // Load
            File tempFile = File.createTempFile("yagoImport", ".gz", new File("./"));
            System.out.println("Loading: " + tempFile.getAbsolutePath());
            PrintWriter tempOut = FileUtils.getPrintWriter(tempFile, Charset.forName("UTF-8"));
            for (String line : FileUtils.getLineStream(input, "UTF-8")) {
                Sentence sent = Gson.fromJson(line, Sentence.class);
                for (QuantitativeFact qfact : sent.quantitativeFacts) {
                    if (qfact.entityTag == null
                            || (qfact.conf > 1e-6 && qfact.conf < minConf)
                            || (qfact.conf <= 1e-6 && Math.exp(qfact.conf) < minConf)) {
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
                    data.put("quantity", qfact.quantityTag.quantity.toString());
                    data.put("sentence", sent.toString());
                    data.put("source", sent.source == null ? "null" : sent.source);
                    data.put("entityStr", sent.getSubTokensString(qfact.entityTag.beginIndex, qfact.entityTag.endIndex));
                    data.put("quantityStr", sent.getSubTokensString(qfact.quantityTag.beginIndex, qfact.quantityTag.endIndex));
                    JSONArray context = new JSONArray();
                    // Build context
                    for (EntityTag t : qfact.contextEntityTags) {
//                        context.put(t.id); // Not using entityID at this time.
                        context.put("<E>:" + sent.getSubTokensString(t.beginIndex, t.endIndex));
                    }
                    for (TimeTag t : qfact.contextTimeTags) {
                        // Not using time range at this time. (we may ignore time in the context when processing
                        // matching).
                        context.put("<T>:" + sent.getSubTokensString(t.beginIndex, t.endIndex));
                    }
                    for (Token t : qfact.contextTokens) {
                        context.put(t.str);
                    }
                    data.put("context", context);
                    tempOut.println(String.format("%s\t%s", entity, data.toString()));
                }
            }
            tempOut.close();

            File newTempFile = File.createTempFile("yagoImport", ".gz", new File("./"));
            System.out.println("Preparing: " + newTempFile.getAbsolutePath());
            ShellCommand.execute("zcat " + tempFile.getAbsolutePath() + " | LC_ALL=C sort | gzip > " + newTempFile.getAbsolutePath());
            tempFile.delete();

            // Import.
            System.out.println("Importing facts");
            Concurrent.BoundedExecutor executor = new Concurrent.BoundedExecutor(8);
            String lastEntity = null;
            JSONArray entityFacts = null;
            for (String line : FileUtils.getLineStream(newTempFile, Charset.forName("UTF-8"))) {
                int pos = line.indexOf("\t");
                String entity = line.substring(0, pos);
                JSONObject data = new JSONObject(line.substring(pos + 1));
                if (lastEntity != null && entity.equals(lastEntity)) {
                    entityFacts.put(data);
                } else {
                    if (lastEntity != null) {
                        final String lE = lastEntity;
                        final JSONArray eF = entityFacts;
                        executor.submit(() -> {
                            importEntityFacts(lE, eF);
                            return null;
                        });
                    }
                    lastEntity = entity;
                    entityFacts = new JSONArray().put(data);
                }
            }
            if (lastEntity != null) {
                final String lE = lastEntity;
                final JSONArray eF = entityFacts;
                executor.submit(() -> {
                    importEntityFacts(lE, eF);
                    return null;
                });
            }
            executor.joinAndShutdown(10);
            newTempFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        System.out.println("Importing facts succeeded.");
        return true;
    }


    public static void main(String[] args) throws Exception {
//        System.out.println(deleteIndex());
//        System.out.println(createIndex());
//        System.out.println("Importing YAGO:");
//        System.out.println(importYago("/home/hvthinh/datasets/yagoTransitiveType.tsv"));
//        System.out.println("Importing facts:");
//        System.out.println(importFacts("./data/stics+nyt/full_all.gz", 0.8));
    }
}
