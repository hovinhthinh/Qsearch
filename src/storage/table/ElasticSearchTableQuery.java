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

    public static final Set<String> BLOCKED_STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't", "as"
            , "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can't",
            "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during"
            , "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't", "have", "haven't", "having",
            "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself", "him", "himself", "his", "how",
            "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's", "its",
            "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no", "nor", "not", "of", "off", "on",
            "once", "only", "or", "other", "ought", "our", "ours	", "ourselves", "out", "over", "own", "same",
            "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such", "than", "that",
            "that's", "the", "their", "theirs", "them", "themselves", "then", "there", "there's", "these", "they",
            "they'd", "they'll", "they're", "they've", "this", "those", "through", "to", "too", "under", "until", "up"
            , "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's",
            "when", "when's", "where", "where's", "which", "while", "who", "who's", "whom", "why", "why's", "with",
            "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself",
            "yourselves"
    ));

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
                            System.out.println("ERR");
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
        private JSONObject result;

        public UpdateInfoCallable(JSONObject result) {
            this.result = result;
        }

        @Override
        public Boolean call() throws Exception {
            try {
                JSONObject o = new JSONObject(HTTPRequest.GET(URL_PREFIX + result.getString("match_table"))).getJSONObject("_source");
                result.put("match_page_content", o.getString("pageContent"));
                Table t;
                synchronized (GSON) {
                    t = GSON.fromJson(o.getString("parsedJson"), Table.class);
                    JSONArray header = new JSONArray();
                    JSONArray content = new JSONArray();
                    for (int r = 0; r < t.nDataRow; ++r) {
                        content.put(new JSONArray());
                    }
                    for (int c = 0; c < t.nColumn; ++c) {
                        header.put(t.getOriginalCombinedHeader(c));
                        for (int r = 0; r < t.nDataRow; ++r) {
                            content.getJSONArray(r).put(t.data[r][c].text);
                        }
                    }
                    result.put("match_header", header);
                    result.put("match_data", content);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static Pair<QuantityConstraint, ArrayList<JSONObject>> search(String fullQuery, int nTopTables, double columnLinkingThreshold,
                                                                         String queryType, String queryContext, String quantityConstraint,
                                                                         int nTopResults, ContextMatcher matcher, Map additionalParameters) {
        quantityConstraint = quantityConstraint.toLowerCase();

        Pair<QuantityConstraint, ArrayList<JSONObject>> result = new Pair<>();

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

        HashMap<String, Pair<JSONObject, Double>> entity2Instance = new HashMap<>();
        try {
            // for each table.
            for (JSONObject o : instances) {
                double elasticScore = o.getDouble("_score");

                Table table;
                synchronized (GSON) {
                    table = GSON.fromJson(o.getJSONObject("_source").getString("parsedJson"), Table.class);
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
                        // TODO: combine with elasticScore

                        // Check with candidate
                        Pair<JSONObject, Double> currentQfact = entity2Instance.get(entity);
                        if (currentQfact != null && currentQfact.second < dist) {
                            continue;
                        }

                        // update best qfact
                        JSONObject newBestQfact = new JSONObject();
                        newBestQfact.put("_id", entity);
                        newBestQfact.put("match_score", dist);
                        newBestQfact.put("match_quantity", qt.toString(1));
                        newBestQfact.put("match_quantity_standard_value", qt.value * QuantityDomain.getScale(qt));
                        newBestQfact.put("match_source", table.source);

                        newBestQfact.put("match_entity_str", el.text);
                        newBestQfact.put("match_quantity_str", ql.text);

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
                        newBestQfact.put("match_quantity_converted_str", matchQuantityConvertedStr);

                        // Table-specific fields
                        newBestQfact.put("match_table", table._id);
                        newBestQfact.put("match_caption", table.caption);
                        newBestQfact.put("match_page_title", table.pageTitle);
                        newBestQfact.put("match_row", row);
                        newBestQfact.put("match_entity_column", table.quantityToEntityColumn[qCol]);
                        newBestQfact.put("match_quantity_column", qCol);
                        String headerUnitStr = table.getHeaderUnitSpan(qCol);
                        newBestQfact.put("match_header_unit_str", headerUnitStr != null ? headerUnitStr : "null");
                        // End of table-specific fields

                        entity2Instance.put(entity, new Pair<>(newBestQfact, dist));
                    }
                }
            }
            if (instances.error) {
                result.second = null;
                return result;
            }
            ArrayList<Pair<JSONObject, Double>> scoredInstances = entity2Instance.values().stream()
                    .sorted((o1, o2) -> o1.second.compareTo(o2.second))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (scoredInstances.size() > nTopResults) {
                scoredInstances.subList(nTopResults, scoredInstances.size()).clear();
            }
            result.second = scoredInstances.stream().map(o -> o.first).collect(Collectors.toCollection(ArrayList::new));

            // update more info for tables: data, header, pageContent
            ArrayList<Future<Boolean>> futures = new ArrayList<>();
            for (JSONObject o : result.second) {
                futures.add(BOUNDED_EXECUTOR.submit(new UpdateInfoCallable(o)));
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

    public static Pair<QuantityConstraint, ArrayList<JSONObject>> search(String fullQuery, int nTopTables, double columnLinkingThreshold,
                                                                         String queryType, String queryContext, String quantityConstraint,
                                                                         int nTopResults, ContextMatcher matcher) {
        return search(fullQuery, nTopTables, columnLinkingThreshold, queryType, queryContext, quantityConstraint, nTopResults, matcher, null);
    }

    public static Pair<QuantityConstraint, ArrayList<JSONObject>> search(String fullQuery, int nTopTables, double columnLinkingThreshold,
                                                                         String queryType, String queryContext, String quantityConstraint,
                                                                         int nTopResults) {
        return search(fullQuery, nTopTables, columnLinkingThreshold, queryType, queryContext, quantityConstraint, nTopResults, DEFAULT_MATCHER);
    }

    public static void main(String[] args) throws Exception {
        ArrayList<JSONObject> result =
                search("cars with fuel consumption more than 0 mpg", 1000, -1,
                        "car", "fuel consumption", "more than 0 mpg",
                        20
                ).second;

        int nPrinted = 0;
        for (JSONObject o : result) {
            try {
                if (nPrinted++ < 1) {
//                    System.out.println(String.format("%30s\t%10.3f\t%50s\t%20s\t%s",
//                            o.getString("_id"),
//                            o.getDouble("match_score"),
//                            o.getString("match_context"),
//                            o.getString("match_quantity"),
//                            o.getString("match_table")
//                    ));
                    System.out.println(o.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        BOUNDED_EXECUTOR.joinAndShutdown(1000);
    }
}
