package storage.table;

import com.google.gson.Gson;
import config.Configuration;
import model.context.ContextEmbeddingMatcher;
import model.context.ContextMatcher;
import model.context.KullBackLeiblerMatcher;
import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import nlp.NLP;
import nlp.YagoType;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import storage.StreamedIterable;
import uk.ac.susx.informatics.Morpha;
import util.Concurrent;
import util.HTTPRequest;
import util.Pair;
import util.headword.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class ElasticSearchTableQuery {
    public static final Logger LOGGER = Logger.getLogger(ElasticSearchTableQuery.class.getName());

    public static final String PROTOCOL = Configuration.get("storage.elasticsearch.protocol");
    public static final String ES_HOST = Configuration.get("storage.elasticsearch.address");
    public static final String TABLE_INDEX = Configuration.get("storage.elasticsearch.table_index");
    public static final String TABLE_TYPE = Configuration.get("storage.elasticsearch.table_type");

    public static ContextMatcher DEFAULT_MATCHER = new ContextEmbeddingMatcher(3);

    public static final Concurrent.BoundedExecutor BOUNDED_EXECUTOR = new Concurrent.BoundedExecutor(32);

    private static Gson GSON = new Gson();

    private static StreamedIterable<JSONObject> searchDocuments(String fullQuery, int nTop) { // nTop = -1 means INF
        // search
        return new StreamedIterable<JSONObject>() {
            private String scroll_id = null;
            private Queue<JSONObject> data = null;
            private String url = PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX + "/" + TABLE_TYPE + "/_search?scroll=5m";
            private String body = "{\n" +
                    "  \"_source\": [\"parsedJson\"],\n" +
                    "  \"size\": 1000,\n" +
                    "  \"query\": {\n" +
                    "    \"bool\": {\n" +
                    "      \"must\": [\n" +
                    "        {\n" +
                    "          \"multi_match\": {\n" +
                    "            \"query\": \"" + fullQuery + "\",\n" +
                    "            \"fields\": [\n" +
                    "              \"caption^5\",\n" +
                    "              \"pageTitle^3\",\n" +
                    "              \"tableText^3\",\n" +
                    "              \"pageContent\"\n" +
                    "            ],\n" +
                    "            \"type\": \"cross_fields\",\n" +
                    "            \"operator\": \"or\"\n" +
                    "          }\n" +
                    "        },\n" +
                    "        {\n" +
                    "          \"exists\": {\n" +
                    "            \"field\": \"searchable\"\n" +
                    "          }\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            private JSONObject nextObject() {
                try {
                    if (data != null && !data.isEmpty()) {
                        return data.poll();
                    }
                    if (scroll_id == null) {
                        String content = HTTPRequest.POST(url, body);
                        if (content == null) {
                            error = true;
                            return null;
                        }
                        JSONObject json = new JSONObject(content);
                        JSONArray arr = json.getJSONObject("hits").getJSONArray("hits");
                        data = new LinkedList<>();
                        for (int i = 0; i < arr.length(); ++i) {
                            data.add(arr.getJSONObject(i));
                        }
                        scroll_id = json.getString("_scroll_id");
                        url = PROTOCOL + "://" + ES_HOST + "/_search/scroll";
                        body = "{\"scroll\":\"5m\",\"scroll_id\":\"" + scroll_id + "\"}";
                        total = json.getJSONObject("hits").getInt("total");
                    } else {
                        String content = HTTPRequest.POST(url, body);
                        if (content == null) {
                            error = true;
                            return null;
                        }
                        JSONArray arr = new JSONObject(content).getJSONObject("hits").getJSONArray("hits");
                        data = new LinkedList<>();
                        for (int i = 0; i < arr.length(); ++i) {
                            data.add(arr.getJSONObject(i));
                        }
                    }
                    return data.poll();
                } catch (Exception e) {
                    e.printStackTrace();
                    error = true;
                    return null;
                }
            }

            @Override
            public Iterator<JSONObject> iterator() {
                return new Iterator<JSONObject>() {
                    private JSONObject currentObj = null;

                    @Override
                    public boolean hasNext() {
                        return (nTop == -1 || (nTop >= 0 && streamed < nTop)) && (currentObj = nextObject()) != null;
                    }

                    @Override
                    public JSONObject next() {
                        if (currentObj != null) {
                            ++streamed;
                        }
                        return currentObj;
                    }
                };
            }
        };
    }

    public static boolean entityHasType(String entity, String queryHeadWord, HashSet<String> queryTypeSet) {
        List<Pair<String, Double>> entityTypes = YagoType.getTypes(entity, false);
        for (Pair<String, Double> p : entityTypes) {
            String type = p.first;
            // check head word
            if (!NLP.getHeadWord(type, true).equals(queryHeadWord)) {
                continue;
            }
            // check all occurrence
            int nOccur = 0;
            // here we assume that all terms of entity type and query type are different.
            for (String t : NLP.splitSentence(type)) {
                if (queryTypeSet.contains(t)) {
                    ++nOccur;
                    if (nOccur == queryTypeSet.size()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    private static class UpdateInfoCallable implements Callable<Boolean> {
        private static final String URL_PREFIX = PROTOCOL + "://" + ES_HOST + "/" + TABLE_INDEX + "/" + TABLE_TYPE + "/";
        private static Gson GSON = new Gson();
        private ResultInstance result;

        public UpdateInfoCallable(ResultInstance result) {
            this.result = result;
        }

        @Override
        public Boolean call() throws Exception {
            try {
                JSONObject o = new JSONObject(HTTPRequest.GET(URL_PREFIX + result.tableId)).getJSONObject("_source");
                result.pageContent = o.getString("pageContent");
                Table t;
                synchronized (GSON) {
                    t = GSON.fromJson(o.getString("parsedJson"), Table.class);
                }
                result.header = new String[t.nColumn];
                result.data = new String[t.nDataRow][t.nColumn];
                for (int c = 0; c < t.nColumn; ++c) {
                    result.header[c] = t.getOriginalCombinedHeader(c);
                    for (int r = 0; r < t.nDataRow; ++r) {
                        result.data[r][c] = t.data[r][c].text;
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String fullQuery, int nTopTables, double columnLinkingThreshold,
                                                                             String queryType, String queryContext, String quantityConstraint,
                                                                             int nTopResults, ContextMatcher matcher, Map additionalParameters) {
        quantityConstraint = quantityConstraint.toLowerCase();

        Pair<QuantityConstraint, ArrayList<ResultInstance>> result = new Pair<>();

        QuantityConstraint constraint = QuantityConstraint.parseFromString(quantityConstraint);
        result.first = constraint;
        if (constraint == null) {
            return result;
        }

        // Build query head word and query type set
        queryType = NLP.stripSentence(NLP.fastStemming(queryType.toLowerCase(), Morpha.noun));
        String queryHeadWord = NLP.getHeadWord(queryType, true);
        HashSet<String> queryTypeSet = new HashSet(NLP.splitSentence(queryType));

        // Process query context terms
        if (QuantityDomain.getDomain(constraint.quantity).equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            queryContext += " " + constraint.quantity.unit;
        }
        ArrayList<String> queryContextTerms = NLP.splitSentence(NLP.fastStemming(queryContext.toLowerCase(), Morpha.any));

        // Search
        fullQuery = fullQuery.toLowerCase();
        StreamedIterable<JSONObject> instances = searchDocuments(fullQuery, nTopTables);

        // Corpus constraint
        ArrayList<String> corpusConstraint = new ArrayList<>();
        synchronized (GSON) {
            corpusConstraint = additionalParameters == null ? null :
                    (additionalParameters.containsKey("corpus") ? GSON.fromJson((String) additionalParameters.get("corpus"), corpusConstraint.getClass()) : null);
        }

        // Explicit matching model
        String explicitMatchingModel = additionalParameters == null ? null : (String) additionalParameters.get("model");
        ContextMatcher explicitMatcher = null;
        if (explicitMatchingModel != null) {
            if (explicitMatchingModel.equals("EMBEDDING")) {
                explicitMatcher = new ContextEmbeddingMatcher((float) additionalParameters.get("alpha"));
            } else {
                explicitMatcher = new KullBackLeiblerMatcher((float) additionalParameters.get("lambda"));
            }
            LOGGER.info("Using explicitly given matcher: " + explicitMatchingModel);
        }

        // retrieve additional parameters
        Session session = additionalParameters == null ? null : (Session) additionalParameters.get("session");
        int lastPercent = 0;

        HashMap<String, ResultInstance> entity2Instance = new HashMap<>();
        try {
            // for each table.
            for (JSONObject o : instances) {
                double elasticScore = o.getDouble("_score");

                Table table;
                JSONObject source = o.getJSONObject("_source");
                synchronized (GSON) {
                    table = GSON.fromJson(source.getString("parsedJson"), Table.class);
                }

                // check corpus target
                if (corpusConstraint != null) {
                    boolean goodSource = false;
                    for (String c : corpusConstraint) {
                        if (table._id.startsWith(c + ":")) {
                            goodSource = true;
                            break;
                        }
                    }
                    if (!goodSource) {
                        // decrease streamed count
                        --instances.streamed;
                        continue;
                    }
                }

                // log progress
                if (session != null) {
                    int currentPercent = instances.total > 0 ? (int) ((double) instances.streamed * 100 / instances.total) : 100;
                    if (currentPercent > lastPercent) {
                        lastPercent = currentPercent;
                        try {
                            session.getRemote().sendString("{\"progress\":" + currentPercent + "}");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                // for all Qfacts
                for (int qCol = 0; qCol < table.nColumn; ++qCol) {
                    if (!table.isNumericColumn[qCol] || (columnLinkingThreshold != -1 && table.quantityToEntityColumnScore[qCol] < columnLinkingThreshold)) {
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

                        Quantity qt = ql.quantity;
                        // quantity constraint
                        if (!constraint.match(qt)) {
                            continue;
                        }

                        // type constraint
                        String entity = "<" + el.target.substring(el.target.lastIndexOf(":") + 1) + ">";
                        if (!entityHasType(entity, queryHeadWord, queryTypeSet)) {
                            continue;
                        }

                        // context
                        ArrayList<String> X = new ArrayList<>(NLP.splitSentence(table.getCombinedHeader(qCol)));
                        if (QuantityDomain.quantityMatchesDomain(qt, QuantityDomain.Domain.DIMENSIONLESS)) {
                            X.addAll(NLP.splitSentence(qt.unit));
                        }
                        for (int j = 0; j < X.size(); ++j) {
                            X.set(j, StringUtils.stem(X.get(j).toLowerCase(), Morpha.any));
                        }
                        if (X.isEmpty()) {
                            continue;
                        }

                        // use explicit matcher if given.
                        double dist = explicitMatcher != null ? explicitMatcher.match(queryContextTerms, X) : matcher.match(queryContextTerms, X);
                        if (Double.isNaN(dist)) {
                            continue;
                        }
                        // TODO: combine with elasticScore

                        // Check with candidate
                        ResultInstance currentQfact = entity2Instance.get(entity);
                        if (currentQfact != null && currentQfact.score <= dist) {
                            continue;
                        }

                        // update best qfact
                        ResultInstance newBestQfact = new ResultInstance();
                        newBestQfact.entity = entity;
                        newBestQfact.score = dist;
                        newBestQfact.quantity = qt.toString(1);
                        newBestQfact.quantityStandardValue = qt.value * QuantityDomain.getScale(qt);
                        newBestQfact.source = table.source;

                        newBestQfact.entityStr = el.text;
                        newBestQfact.quantityStr = ql.text;

                        // Get quantity converted string.
                        String matchQuantityConvertedStr;
                        double scale = QuantityDomain.getScale(qt) / QuantityDomain.getScale(constraint.quantity);
                        if (Math.abs(scale - 1.0) >= 1e-6) {
                            double convertedValue = scale * qt.value;
                            if (Math.abs(convertedValue) >= 1e9) {
                                matchQuantityConvertedStr = String.format("%.1f", convertedValue / 1e9) + " billion";
                            } else if (convertedValue >= 1e6) {
                                matchQuantityConvertedStr = String.format("%.1f", convertedValue / 1e6) + " million";
                            } else if (convertedValue >= 1e5) {
                                matchQuantityConvertedStr = String.format("%.0f", convertedValue / 1e3) + " thousand";
                            } else {
                                matchQuantityConvertedStr = String.format("%.2f", convertedValue);
                            }
                            matchQuantityConvertedStr += " (" + constraint.quantity.unit + ")";
                        } else {
                            matchQuantityConvertedStr = "null";
                        }
                        newBestQfact.quantityConvertedStr = matchQuantityConvertedStr;

                        // Table-specific fields
                        newBestQfact.elasticScore = elasticScore;
                        newBestQfact.QELinkingScore = table.quantityToEntityColumnScore[qCol];

                        newBestQfact.tableId = table._id;
                        newBestQfact.caption = source.getString("caption");
                        newBestQfact.pageTitle = source.getString("pageTitle");
                        newBestQfact.row = row;
                        newBestQfact.entityColumn = table.quantityToEntityColumn[qCol];
                        newBestQfact.quantityColumn = qCol;
                        String headerUnitStr = table.getHeaderUnitSpan(qCol);
                        newBestQfact.headerUnitSpan = headerUnitStr != null ? headerUnitStr : "null";
                        // End of table-specific fields

                        entity2Instance.put(entity, newBestQfact);
                    }
                }
            }
            if (instances.error) {
                result.second = null;
                return result;
            }
            result.second = entity2Instance.values().stream()
                    .sorted((o1, o2) -> {
                        int compare = Double.compare(o1.score, o2.score);
                        if (compare != 0) return compare;
                        compare = Double.compare(o2.elasticScore, o1.elasticScore);
                        if (compare != 0) return compare;
                        compare = o1.tableId.compareTo(o2.tableId);
                        if (compare != 0) return compare;
                        return Double.compare(o1.row, o2.row);
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

            // update more info for tables: data, header, pageContent (only for nTopResults results)
            ArrayList<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < Math.min(nTopResults, result.second.size()); ++i) {
                futures.add(BOUNDED_EXECUTOR.submit(new UpdateInfoCallable(result.second.get(i))));
            }
            for (Future<Boolean> f : futures) {
                if (f.get() == false) {
                    result.second = null;
                    return result;
                }
            }
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            return result;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            result.second = null;
            return result;
        }
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String fullQuery, int nTopTables, double columnLinkingThreshold,
                                                                             String queryType, String queryContext, String quantityConstraint,
                                                                             int nTopResults, ContextMatcher matcher) {
        return search(fullQuery, nTopTables, columnLinkingThreshold, queryType, queryContext, quantityConstraint, nTopResults, matcher, null);
    }

    public static Pair<QuantityConstraint, ArrayList<ResultInstance>> search(String fullQuery, int nTopTables, double columnLinkingThreshold,
                                                                             String queryType, String queryContext, String quantityConstraint,
                                                                             int nTopResults) {
        return search(fullQuery, nTopTables, columnLinkingThreshold, queryType, queryContext, quantityConstraint, nTopResults, DEFAULT_MATCHER);
    }

    public static void main(String[] args) throws Exception {
        ArrayList<ResultInstance> result =
                search("technology companies having at least 1 billion usd annual profit", 1000, -1,
                        "technology companies", "annual profit", "at least 1 billion usd",
                        20
                ).second;
        Gson gson = new Gson();
        int nPrinted = 0;
        for (ResultInstance o : result) {
            try {
                if (nPrinted++ < 20) {
                    System.out.println(String.format("%30s\t%10.3f\t%50s\t%20s\t%s",
                            o.entity,
                            o.score,
                            o.contextStr,
                            o.quantity,
                            o.tableId)
                    );
                    System.out.println(gson.toJson(o));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        BOUNDED_EXECUTOR.joinAndShutdown(1000);
    }
}
