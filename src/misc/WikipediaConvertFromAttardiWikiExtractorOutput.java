package misc;

import org.json.JSONArray;
import org.json.JSONObject;
import util.FileUtils;
import util.Quadruple;
import util.SelfMonitor;

import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikipediaConvertFromAttardiWikiExtractorOutput {

    static Pattern startPattern = Pattern.compile("&lt;a\\s+href=\""),
            midPattern = Pattern.compile("\"\\s*&gt;"), endPattern = Pattern.compile("&lt;/a\\s*&gt;");

    // surface, link, start, end
    static Quadruple<String, String, Integer, Integer> findNext(String text, int cur) {
        Matcher m1 = startPattern.matcher(text);
        if (!m1.find(cur)) {
            return null;
        }
        Quadruple<String, String, Integer, Integer> result = new Quadruple<>();

        result.third = m1.start();

        Matcher m2 = midPattern.matcher(text);
        if (!m2.find(m1.end())) {
            return null;
        }
        try {
            result.second = URLDecoder.decode(text.substring(m1.end(), m2.start()), "UTF-8");
        } catch (Exception e) {
            return null;
        }

        Matcher m3 = endPattern.matcher(text);
        if (!m3.find(m2.end())) {
            return null;
        }

        result.fourth = m3.end();
        result.first = text.substring(m2.end(), m3.start());

        return result;
    }

    public static void main(String[] args) {
        String input = "/GW/D5data-14/hvthinh/enwiki-09-2021/parsed_by_attardi_wikiextractor.json.gz";
        String output = "/GW/D5data-14/hvthinh/enwiki-09-2021/standardized.json.gz";
        HashSet<String> validEntitySet = new HashSet<>();

        // Get a valid set of entities
        int totalValidPages = 0;
        for (String line : FileUtils.getLineStream(input, "UTF-8")) {
            JSONObject o = new JSONObject(line);
            if (!o.getString("text").isEmpty()) {
                validEntitySet.add(o.getString("title"));
                ++totalValidPages;
            }
        }

        // Now process
        SelfMonitor m = new SelfMonitor(null, totalValidPages, 10);
        m.start();

        PrintWriter out = FileUtils.getPrintWriter(output);
        for (String line : FileUtils.getLineStream(input, "UTF-8")) {
            JSONObject o = new JSONObject(line);
            if (o.getString("text").isEmpty()) {
                continue;
            }

            JSONObject res = new JSONObject();

            res.put("source", o.getString("url"));
            res.put("title", o.getString("title"));

            Map<String, Set<String>> entity2Mentions = new LinkedHashMap<>();
            String entity = "<" + o.getString("title").replace(' ', '_') + ">";
            entity2Mentions.put(entity, new HashSet<>() {{
                add(o.getString("title"));
            }});

            StringBuilder processedText = new StringBuilder();

            String text = o.getString("text");

            Quadruple<String, String, Integer, Integer> match = null;

            int cur = 0;
            while ((match = findNext(text, cur)) != null) {
                processedText.append(text, cur, match.third).append(match.first);
                cur = match.fourth;

                if (validEntitySet.contains(match.second)) {
                    entity = "<" + match.second.replace(' ', '_') + ">";
                    if (!entity2Mentions.containsKey(entity)) {
                        entity2Mentions.put(entity, new HashSet<>());
                    }
                    entity2Mentions.get(entity).add(match.first);
                }
            }
            processedText.append(text.substring(cur));

            res.put("content", processedText.toString());
            JSONObject e2mJson = new JSONObject();
            entity2Mentions.forEach((k, v) -> {
                JSONArray arr = new JSONArray();
                for (String s : v) {
                    arr.put(s);
                }
                if (arr.length() < 2) {
                    e2mJson.put(k, arr.getString(0));
                } else {
                    e2mJson.put(k, arr);
                }
            });
            res.put("entities", e2mJson);
            out.println(res);
            m.incAndGet();
        }
        out.close();
        m.forceShutdown();
    }
}
