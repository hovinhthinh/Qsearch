package storage.table.experimental;


import config.Configuration;
import model.context.ContextEmbeddingMatcher;
import model.context.ContextMatcher;
import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import nlp.NLP;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.ac.susx.informatics.Morpha;
import util.Constants;
import util.HTTPRequest;
import util.Pair;
import util.headword.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Deprecated
abstract class StreamedIterable<T> implements Iterable<T> {
    public boolean error = false;
    public int total = -1;
    public int streamed = 0;
}

@Deprecated
public class ElasticSearchQuery {
    public static final String PROTOCOL = Configuration.get("storage.elasticsearch.protocol");
    public static final String ES_HOST = Configuration.get("storage.elasticsearch.address");
    public static final String INDEX = "table_entity";
    public static final String TYPE = "tentity";

    public static ContextMatcher DEFAULT_MATCHER = new ContextEmbeddingMatcher(3);

    private static StreamedIterable<JSONObject> searchRawES(String queryType) {
        // remove stopwords
        StringBuilder sb = new StringBuilder();
        for (String t : NLP.splitSentence(queryType)) {
            if (!NLP.BLOCKED_STOPWORDS.contains(t.toLowerCase())) {
                sb.append(" ").append(t);
            }
        }
        String optimizedQueryType = sb.toString().trim();
        // search
        return new StreamedIterable<JSONObject>() {
            private String scroll_id = null;
            private Queue<JSONObject> data = null;
            private String url = PROTOCOL + "://" + ES_HOST + "/" + INDEX + "/" + TYPE + "/_search?scroll=5m";
            private String body =
                    "{\"size\":1000,\"query\":{\"bool\":{\"must\":[{\"nested\":{\"path\":\"types\"," +
                            "\"score_mode\":\"max\",\"query\":{\"match\":{\"types.value\":{\"query\":\"" + optimizedQueryType +
                            "\",\"operator\":\"and\"}}}}},{\"exists\":{\"field\":\"searchable\"}}]}}}";

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
                        return (currentObj = nextObject()) != null;
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

    public static StreamedIterable<JSONObject> searchByType(String queryType) {
        final String optimizedQueryType = NLP.stripSentence(NLP.fastStemming(queryType.toLowerCase(), Morpha.noun));

        return new StreamedIterable<JSONObject>() {
            // Get preResult from ElasticSearch. (Result from ES is very noisy).
            private StreamedIterable<JSONObject> preResult = searchRawES(optimizedQueryType);

            // Now, filter the preResult, check if headword of the query is also headword of some type of the entity.
            private String searchingHead = NLP.getHeadWord(optimizedQueryType, true);
            private Iterator<JSONObject> it = preResult.iterator();

            private JSONObject nextObject() {
                do {
                    if (!it.hasNext()) {
                        break;
                    }
                    JSONObject o = it.next();
                    total = preResult.total;
                    streamed = preResult.streamed;
                    try {
                        JSONArray types = o.getJSONObject("_source").getJSONArray("types");
                        boolean good = false;
                        for (int i = 0; i < types.length(); ++i) {
                            String t = types.getJSONObject(i).getString("value");
                            if (NLP.getHeadWord(t, true).equals(searchingHead)) {
                                good = true;
                                break;
                            }
                        }
                        if (good) {
                            return o;
                        }
                    } catch (Exception e) {
                    }
                } while (true);
                error = preResult.error;
                return null;
            }

            @Override
            public Iterator<JSONObject> iterator() {
                return new Iterator<JSONObject>() {
                    private JSONObject currentObj = null;

                    @Override
                    public boolean hasNext() {
                        return (currentObj = nextObject()) != null;
                    }

                    @Override
                    public JSONObject next() {
                        return currentObj;
                    }
                };
            }
        };
    }

    public static Pair<QuantityConstraint, ArrayList<JSONObject>> search(String queryType, String queryContext,
                                                                         String quantityConstraint,
                                                                         ContextMatcher matcher, Session session) {
        queryType = queryType.toLowerCase();
        queryContext = queryContext.toLowerCase();
        quantityConstraint = quantityConstraint.toLowerCase();

        Pair<QuantityConstraint, ArrayList<JSONObject>> result = new Pair<>();

        QuantityConstraint constraint = QuantityConstraint.parseFromString(quantityConstraint);
        result.first = constraint;
        if (constraint == null) {
            return result;
        }

        StreamedIterable<JSONObject> instances = searchByType(queryType);

        if (QuantityDomain.getDomain(constraint.quantity) == QuantityDomain.Domain.DIMENSIONLESS) {
            queryContext += " " + constraint.quantity.unit;
        }
        ArrayList<String> queryContextTerms =
                NLP.splitSentence(NLP.fastStemming(queryContext.toLowerCase(), Morpha.any));
        ArrayList<Pair<JSONObject, Double>> scoredInstances = new ArrayList<>();
        int lastPercent = 0;
        try {
            // for each entity.
            for (JSONObject o : instances) {
                String matchContext = "null";
                String matchQuantity = "null";
                String matchSentence = "null";
                String matchSource = "null";
                String matchEntityStr = "null";
                String matchQuantityStr = "null";
                String matchQuantityConvertedStr = "null";

                // computer score
                double minDist = Constants.MAX_DOUBLE;
                JSONArray facts = o.getJSONObject("_source").getJSONArray("facts");
                // save space.
                o.put("_source", "<OMITTED>");
                for (int i = 0; i < facts.length(); ++i) {
                    String Q = facts.getJSONObject(i).getString("quantity");
                    Quantity qt = Quantity.fromQuantityString(Q);
                    if (!constraint.match(qt)) {
                        continue;
                    }

                    ArrayList<String> X = new ArrayList<>();
                    JSONArray context = facts.getJSONObject(i).getJSONArray("context");
                    for (int k = 0; k < context.length(); ++k) {
                        String ct = context.getString(k);
                        if (ct.startsWith("<T>:")) {
                            // handle time like normal terms.
                            X.addAll(NLP.splitSentence(ct.substring(4)));
                        } else if (ct.startsWith("<E>:")) {
                            X.addAll(NLP.splitSentence(ct.substring(4)));
                        } else {
                            X.add(ct);
                        }
                    }
                    if (QuantityDomain.quantityMatchesDomain(qt, QuantityDomain.Domain.DIMENSIONLESS)) {
                        X.addAll(NLP.splitSentence(qt.unit));
                    }
                    if (X.isEmpty()) {
                        continue;
                    }
                    for (int j = 0; j < X.size(); ++j) {
                        X.set(j, StringUtils.stem(X.get(j).toLowerCase(), Morpha.any));
                    }
                    double dist = matcher.match(queryContextTerms, X);
                    if (dist < minDist) {
                        minDist = dist;
                        StringBuilder contextString = new StringBuilder();
                        X.forEach(e -> {
                            if (contextString.length() > 0) {
                                contextString.append(" ");
                            }
                            contextString.append(e);
                        });
                        matchContext = contextString.toString();
                        matchQuantity = qt.toString(1);
                        matchSentence = facts.getJSONObject(i).getString("sentence");
                        matchSource = facts.getJSONObject(i).getString("source");

                        matchEntityStr = facts.getJSONObject(i).getString("entityStr");
                        matchQuantityStr = facts.getJSONObject(i).getString("quantityStr");

                        // Get quantity converted string.
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
                    }
                }
                if (matchQuantity.equals("null")) {
                    continue;
                }
                o.put("match_score", minDist);
                o.put("match_context", matchContext);
                o.put("match_quantity", matchQuantity);
                o.put("match_sentence", matchSentence);
                o.put("match_source", matchSource);

                o.put("match_entity_str", matchEntityStr);
                o.put("match_quantity_str", matchQuantityStr);

                o.put("match_quantity_converted_str", matchQuantityConvertedStr);

                scoredInstances.add(new Pair<>(o, minDist));
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
            }
            if (instances.error) {
                result.second = null;
                return result;
            }
            Collections.sort(scoredInstances, (o1, o2) -> o1.second.compareTo(o2.second));
            result.second = scoredInstances.stream().map(o -> o.first).collect(Collectors.toCollection(ArrayList::new));
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            return result;
        }
    }

    public static Pair<QuantityConstraint, ArrayList<JSONObject>> search(String queryType, String queryContext,
                                                                         String quantityConstraint,
                                                                         ContextMatcher matcher) {
        return search(queryType, queryContext, quantityConstraint, matcher, null);
    }

    public static Pair<QuantityConstraint, ArrayList<JSONObject>> search(String queryType, String queryContext,
                                                                         String quantityConstraint) {
        return search(queryType, queryContext, quantityConstraint, DEFAULT_MATCHER);
    }

    public static void main(String[] args) {
        ArrayList<JSONObject> result =
                search("car",
                        "consumption",
                        "more than 0 mpg").second;

        int nPrinted = 0;
        for (JSONObject o : result) {
            try {
                if (nPrinted < 20) {
                    System.out.println(String.format("%30s\t%10.3f\t%50s\t%20s\t%s\t%s", o.getString("_id"),
                            o.getDouble(
                                    "match_score"),
                            o.getString("match_context"), o.getString("match_quantity"),
                            o.getString("match_sentence"), o.getString("match_source")));
                    ++nPrinted;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
