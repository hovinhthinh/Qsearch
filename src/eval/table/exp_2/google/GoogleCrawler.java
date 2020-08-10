package eval.table.exp_2.google;

import eval.table.exp_2.recall.RecallQuery;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONObject;
import util.Crawler;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;


public class GoogleCrawler {
    static ArrayList<String> KEYS = new ArrayList<>(Arrays.asList(
            "AIzaSyDzMkMyxiRvFC8grHH9GTgUepMmysL-e-g",
            "AIzaSyBlYSw_13wDdDXtZmRmBkYZXQlwftP7Xmw",
            "AIzaSyCjlVwWA95UC-KDylTwhgd-fGIlU1RAzIk",
            "AIzaSyANfb7eo_XccvkLgRkuwytojB668tt8dG8"
    ));

    public static final String PREFIX = "https://www.googleapis.com/customsearch/v1?key={KEY}"
            + "&cx=013281578850290927755:oigpvefqo6g"
            + "&start=1&num=10&q=";

    public static ArrayList<String> query(String query) {
        boolean first = true;
        for (String key : KEYS) {
            if (first) {
                first = false;
            } else {
                System.out.println("retry with key: " + key);
            }
            try {
                String url = PREFIX.replace("{KEY}", key) + URLEncoder.encode(query, "UTF-8");
                String content = Crawler.getContentFromUrl(url);
                JSONObject o = new JSONObject(content);
                JSONArray r = o.getJSONArray("items");

                ArrayList<String> arr = new ArrayList<>();
                for (int i = 0; i < r.length(); ++i) {
                    String title = r.getJSONObject(i).getString("title");
                    String snippet = r.getJSONObject(i).getString("snippet");
                    String link = r.getJSONObject(i).getString("link");

                    title = NLP.stripSentence(title);
                    snippet = NLP.stripSentence(snippet);
                    link = NLP.stripSentence(link);
                    arr.add(title + "\t" + snippet + "\t" + link);
                }
                return arr;
            } catch (Exception e) {
                continue;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        PrintWriter out = FileUtils.getPrintWriter("eval/table/exp_2/recall/baseline_google_top10.tsv", "UTF-8");
        out.println("query_id\tquery\ttitle\tsnippet\tlink\tresult_pos");
        int queryId = 0;
        for (String line : FileUtils.getLineStream("eval/table/exp_2/recall/recall_query.json", "UTF-8")) {
            RecallQuery r = Gson.fromJson(line, RecallQuery.class);
            ++queryId;
            ArrayList<String> result = query(r.full);

            try {
                for (int i = 0; i < result.size(); ++i) {
                    if (i >= 10) {
                        break;
                    }
                    String[] x = result.get(i).split("\t");
                    out.println(queryId + "\t" + r.full + "\t" + x[0].replaceAll("\"", "'")
                            + "\t" + x[1].replaceAll("\"", "'") + "\t" + x[2]
                            + "\t" + (i + 1));
                }
                System.out.println("DONE: " + r.full);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error: " + r.full);
                continue;
            }
        }
        out.close();
    }
}

