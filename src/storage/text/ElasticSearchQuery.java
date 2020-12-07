package storage.text;

import config.Configuration;
import misc.WikipediaView;
import model.context.ContextEmbeddingMatcher;
import model.context.ContextMatcher;
import model.context.KullBackLeiblerMatcher;
import model.quantity.Quantity;
import model.quantity.QuantityConstraint;
import model.quantity.QuantityDomain;
import nlp.NLP;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import server.text.handler.search.SearchResult;
import storage.StreamedIterable;
import uk.ac.susx.informatics.Morpha;
import util.Constants;
import util.Gson;
import util.HTTPRequest;
import util.Pair;
import util.headword.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

@Deprecated
public class ElasticSearchQuery {
    public static final Logger LOGGER = Logger.getLogger(ElasticSearchQuery.class.getName());

    public static final String PROTOCOL = Configuration.get("elasticsearch.protocol");
    public static final String ES_HOST = Configuration.get("elasticsearch.address");
    public static final String INDEX = Configuration.get("elasticsearch.text.index");
    public static final String TYPE = Configuration.get("elasticsearch.text.type");

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

    public static Pair<QuantityConstraint, ArrayList<SearchResult.ResultInstance>> search(String queryType, String queryContext,
                                                                                          String quantityConstraint,
                                                                                          ContextMatcher matcher, Map additionalParameters) {
        queryType = queryType.toLowerCase();
        queryContext = queryContext.toLowerCase();

        Pair<QuantityConstraint, ArrayList<SearchResult.ResultInstance>> result = new Pair<>();

        QuantityConstraint constraint = QuantityConstraint.parseFromString(quantityConstraint);
        result.first = constraint;
        if (constraint == null) {
            return result;
        }

        StreamedIterable<JSONObject> instances = searchByType(queryType);

        // Process query context terms
        String domain = QuantityDomain.getDomain(constraint.quantity);
        if (domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            queryContext += " " + constraint.quantity.unit;
        }
        queryContext = NLP.fastStemming(queryContext.toLowerCase(), Morpha.any);
        // expand with domain name if empty
        if (queryContext.isEmpty() && !domain.equals(QuantityDomain.Domain.DIMENSIONLESS)) {
            queryContext = NLP.stripSentence(domain.toLowerCase());
        }
        ArrayList<String> queryContextTerms = NLP.splitSentence(queryContext);

        ArrayList<SearchResult.ResultInstance> scoredInstances = new ArrayList<>();
        int lastPercent = 0;

        // retrieve additional parameters
        Session session = additionalParameters == null ? null : (Session) additionalParameters.get("session");

        ArrayList<String> corpusConstraint = new ArrayList<>();
        corpusConstraint = additionalParameters == null ? null :
                (additionalParameters.containsKey("corpus") ? Gson.fromJson((String) additionalParameters.get("corpus"), corpusConstraint.getClass()) : null);

        String explicitMatchingModel = additionalParameters == null ? null : (String) additionalParameters.get("model");
        ContextMatcher explicitMatcher = null;
        if (explicitMatchingModel != null) {
            if (explicitMatchingModel.equals("EMBEDDING")) {
                explicitMatcher = new ContextEmbeddingMatcher((double) additionalParameters.get("alpha"));
            } else {
                explicitMatcher = new KullBackLeiblerMatcher((double) additionalParameters.get("lambda"));
            }
            LOGGER.info("Using explicitly given matcher: " + explicitMatchingModel);
        }

        try {
            // for each entity.
            for (JSONObject o : instances) {
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
//                ArrayList<String> matchContext = new ArrayList<>();

                // computer score
                JSONArray facts = o.getJSONObject("_source").getJSONArray("facts");
                // save space.
                o.put("_source", "<OMITTED>");
                SearchResult.ResultInstance r = new SearchResult.ResultInstance();
                r.score = Constants.MAX_DOUBLE;
                r.entity = o.getString("_id");
                r.popularity = WikipediaView.getView(r.entity);
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

                    ArrayList<String> contextVerbose = new ArrayList<>(X);

                    if (QuantityDomain.quantityMatchesDomain(qt, QuantityDomain.Domain.DIMENSIONLESS)) {
                        X.addAll(NLP.splitSentence(qt.unit));
                    }
                    if (X.isEmpty()) {
                        continue;
                    }
                    for (int j = 0; j < X.size(); ++j) {
                        X.set(j, StringUtils.stem(X.get(j).toLowerCase(), Morpha.any));
                    }
                    // use explicit matcher if given.
                    double dist = explicitMatcher != null ? explicitMatcher.match(queryContextTerms, X) : matcher.match(queryContextTerms, X);

                    if (dist < r.score) {
                        r.score = dist;
                        r.quantity = qt.toString(1);
                        r.quantityStandardValue = qt.value * QuantityDomain.getScale(qt);
                        r.quantityStr = facts.getJSONObject(i).getString("quantityStr");
                        r.quantityConvertedStr = qt.getQuantityConvertedStr(constraint.quantity);

                        r.entityStr = facts.getJSONObject(i).getString("entityStr");

                        r.contextStr = contextVerbose;
//                        matchContext = new ArrayList<>(X);

                        r.sentence = facts.getJSONObject(i).getString("sentence");
                        r.source = facts.getJSONObject(i).getString("source");
                    }
                }
                if (r.quantity != null) {
                    scoredInstances.add(r);
                }
            }
            if (instances.error) {
                result.second = null;
                return result;
            }
            Collections.sort(scoredInstances);

            result.second = scoredInstances;
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            return result;
        }
    }

    public static Pair<QuantityConstraint, ArrayList<SearchResult.ResultInstance>> search(String queryType, String queryContext,
                                                                                          String quantityConstraint,
                                                                                          ContextMatcher matcher) {
        return search(queryType, queryContext, quantityConstraint, matcher, null);
    }

    public static Pair<QuantityConstraint, ArrayList<SearchResult.ResultInstance>> search(String queryType, String queryContext,
                                                                                          String quantityConstraint) {
        return search(queryType, queryContext, quantityConstraint, DEFAULT_MATCHER);
    }

    public static void main(String[] args) {
        ArrayList<SearchResult.ResultInstance> result =
                search("car",
                        "consumption",
                        "more than 0 mpg").second;

        int nPrinted = 0;
        for (SearchResult.ResultInstance o : result) {
            try {
                if (nPrinted < 20) {
                    System.out.println(String.format("%30s\t%10.3f\t%50s\t%20s\t%s\t%s", o.entity,
                            o.score, Gson.toJson(o.contextStr), o.quantity, o.sentence, o.source));
                    ++nPrinted;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
