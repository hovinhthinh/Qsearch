package storage.table.experimental;

import com.google.gson.Gson;
import config.Configuration;
import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import nlp.NLP;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pipeline.TextBasedColumnScoringNode;
import uk.ac.susx.informatics.Morpha;
import util.Concurrent;
import util.FileUtils;
import util.HTTPRequest;
import util.ShellCommand;
import util.headword.StringUtils;
import yago.TaxonomyGraph;

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
    public static final String INDEX = "table_entity";
    public static final String TYPE = "entity";
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
            String type = TaxonomyGraph.textualize(arr[3]);
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
            Gson gson = new Gson();
            File tempFile = File.createTempFile("yagoImport", ".gz", new File("./"));
            System.out.println("Loading: " + tempFile.getAbsolutePath());
            PrintWriter tempOut = FileUtils.getPrintWriter(tempFile, Charset.forName("UTF-8"));
            for (String line : FileUtils.getLineStream(input, "UTF-8")) {
                Table table = gson.fromJson(line, Table.class);
                // for all Qfacts
                for (int qCol = 0; qCol < table.nColumn; ++qCol) {
                    if (!table.isNumericColumn[qCol] || (minConf != -1 && table.quantityToEntityColumnScore[qCol] < minConf)) {
                        continue;
                    }

                    for (int row = 0; row < table.nDataRow; ++row) {
                        QuantityLink ql = table.data[row][qCol].getRepresentativeQuantityLink();
                        if (ql == null) {
                            continue;
                        }
                        EntityLink el = table.data[row][table.quantityToEntityColumn[qCol]].getRepresentativeEntityLink();
                        if (el == null) {
                            continue;
                        }

                        String entity = el.target;
                        if (entity.startsWith("YAGO:")) {
                            entity = "<" + entity.substring(5) + ">";
                        }

                        Quantity qt = ql.quantity;

                        String domain = QuantityDomain.getDomain(qt);
                        // context
                        ArrayList<String> X = new ArrayList<>(NLP.splitSentence(table.getCombinedHeader(qCol)));
                        if (domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
                            X.addAll(NLP.splitSentence(qt.unit));
                            qt.unit = "";
                        }
                        for (int j = X.size() - 1; j >= 0; --j) {
                            X.set(j, StringUtils.stem(X.get(j).toLowerCase(), Morpha.any));
                            if (NLP.BLOCKED_STOPWORDS.contains(X.get(j)) || TextBasedColumnScoringNode.BLOCKED_OVERLAP_CONTEXT_TOKENS.contains(X.get(j))) {
                                X.remove(j);
                            }
                        }
                        if (X.isEmpty()) {
                            continue;
                        }

                        JSONArray context = new JSONArray();
                        for (String x : X) {
                            context.put(x);
                        }
                        JSONObject data = new JSONObject();
                        data.put("context", context);
                        data.put("quantity", qt.toString());
                        data.put("sentence", "null");
                        data.put("source", table.source.replace("WIKIPEDIA:Link:", "").replace("TABLEM:Link:", ""));
                        tempOut.println(String.format("%s\t%s", entity, data.toString()));
                    }
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
        System.out.println(deleteIndex());
        System.out.println(createIndex());
        System.out.println("Importing YAGO:");
        System.out.println(importYago("/GW/D5data-10/hvthinh/yagoTransitiveType.tsv"));
        System.out.println("Importing facts:");
        System.out.println(importFacts("/GW/D5data-12/hvthinh/TabQs/annotation+linking/wiki+tablem_annotation_linking.gz", 0.7));
    }
}
