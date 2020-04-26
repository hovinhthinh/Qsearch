package storage.table.experimental;

import com.google.gson.Gson;
import config.Configuration;
import model.context.ContextMatcher;
import model.context.IDF;
import model.quantity.Quantity;
import model.quantity.QuantityDomain;
import nlp.Glove;
import nlp.NLP;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.ac.susx.informatics.Morpha;
import util.HTTPRequest;
import util.headword.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

abstract class StreamedIterable<T> implements Iterable<T> {
    public boolean error = false;
    public int total = -1;
    public int streamed = 0;
}

@Deprecated
public class ElasticSearchQuery {
    public static final Logger LOGGER = Logger.getLogger(ElasticSearchQuery.class.getName());

    public static final String PROTOCOL = Configuration.get("storage.elasticsearch.protocol");
    public static final String ES_HOST = Configuration.get("storage.elasticsearch.address");
    public static final String INDEX = "table_entity";
    public static final String TYPE = "entity";

    public static ContextMatcher DEFAULT_MATCHER = new ContextMatcher() {
        // Higher is better
        // range from 0 -> 1
        public double directedEmbeddingIdfSimilarity(ArrayList<String> queryX, ArrayList<String> factX) {
            // TODO: Currently not supporting TIME (TIME is computed like normal terms).
            if (queryX.isEmpty() || factX.isEmpty()) {
                return queryX.isEmpty() && factX.isEmpty() ? 1 : 0;
            }
            double score = 0;
            double totalIdf = 0;
            for (String qX : queryX) {
                double max = 0;
                for (String fX : factX) {
                    double sim = Glove.cosineDistance(qX, fX);
                    if (sim != -1) {
                        max = Math.max(max, 1 - sim);
                    }
                }
                double idf = IDF.getRobertsonIdf(qX);
                score += max * idf;
                totalIdf += idf;
            }
            return score / totalIdf;
        }

        @Override
        public double match(ArrayList<String> queryContext, ArrayList<String> factContext) {
            return directedEmbeddingIdfSimilarity(queryContext, factContext);
        }
    };

    private static Gson GSON = new Gson();

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

    public static ArrayList<ResultInstance> searchWithoutQuantityConstraint(String queryType, String queryContext, Map additionalParameters) {
        queryType = queryType.toLowerCase();
        queryContext = queryContext.toLowerCase();

        ArrayList<ResultInstance> result = new ArrayList<>();

        StreamedIterable<JSONObject> instances = searchByType(queryType);

        // Process query context terms
        ArrayList<String> queryContextTerms = NLP.splitSentence(NLP.fastStemming(queryContext.toLowerCase(), Morpha.any));

        // retrieve additional parameters
        Session session = additionalParameters == null ? null : (Session) additionalParameters.get("session");
        int lastPercent = 0;

        // Corpus constraint
        ArrayList<String> corpusConstraint = new ArrayList<>();
        synchronized (GSON) {
            corpusConstraint = additionalParameters == null ? null :
                    (additionalParameters.containsKey("corpus") ? GSON.fromJson((String) additionalParameters.get("corpus"), corpusConstraint.getClass()) : null);
        }

        try {
            // for each entity.
            for (JSONObject o : instances) {
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

                JSONArray facts = o.getJSONObject("_source").getJSONArray("facts");
                // save space.
                o.put("_source", "<OMITTED>");
                ResultInstance inst = new ResultInstance();
                inst.entity = o.getString("_id");

                for (int i = 0; i < facts.length(); ++i) {
                    // check corpus target
                    if (corpusConstraint != null) {
                        boolean goodSource = false;
                        for (String c : corpusConstraint) {
                            if (facts.getJSONObject(i).getString("source").startsWith(c + ":")) {
                                goodSource = true;
                                break;
                            }
                        }
                        if (!goodSource) {
                            continue;
                        }
                    }

                    // quantity
                    String Q = facts.getJSONObject(i).getString("quantity");
                    Quantity qt = Quantity.fromQuantityString(Q);

                    // context
                    ArrayList<String> X = new ArrayList<>();
                    JSONArray context = facts.getJSONObject(i).getJSONArray("context");
                    for (int k = 0; k < context.length(); ++k) {
                        X.add(context.getString(k));
                    }
                    String contextVerbose;
                    synchronized (GSON) {
                        contextVerbose = GSON.toJson(X);
                    }
                    if (X.isEmpty()) {
                        continue;
                    }

                    ResultInstance.SubInstance si = new ResultInstance.SubInstance();
                    si.quantity = qt.toString(2);
                    si.context = contextVerbose;
                    si.domain = QuantityDomain.getDomain(qt);
                    si.source = facts.getJSONObject(i).getString("source");

                    // match
                    for (int j = 0; j < X.size(); ++j) {
                        X.set(j, StringUtils.stem(X.get(j).toLowerCase(), Morpha.any));
                    }
                    // use explicit matcher if given.
                    si.score = DEFAULT_MATCHER.match(queryContextTerms, X);
                    if (si.score < 0.7) {
                        continue;
                    }
                    inst.addSubInstance(si);
                }

                if (inst.subInstances.size() > 0) {
                    Collections.sort(inst.subInstances, (o1, o2) -> Double.compare(o2.score, o1.score));
                    result.add(inst);
                }
            }
            if (instances.error) {
                return null;
            }
            Collections.sort(result, (o1, o2) -> Double.compare(o2.score, o1.score));
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            return result;
        }
    }

    public static void main(String[] args) {
        Gson g = new Gson();
        ArrayList<ResultInstance> res = searchWithoutQuantityConstraint("businessperson", "net worth", null);
        res.subList(5, res.size()).clear();
        System.out.println(g.toJson(res));
    }
}
