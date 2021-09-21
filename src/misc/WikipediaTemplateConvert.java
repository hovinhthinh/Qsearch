package misc;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import util.Crawler;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class WikipediaTemplateConvert {
    static class TemplateUnit {
        String code;
        String abbr;
        String name, pluralName;
        String link;
    }

    static void crawl() {
        String content = Crawler.getContentFromUrl("https://en.wikipedia.org/wiki/Module:Convert/documentation/conversion_data");

        Document doc = Jsoup.parse(content);
        Elements elements = doc.select(".wikitable");

        Map<String, TemplateUnit> units = new HashMap<>();

        for (Element e : elements) {
            if (e.select("th").size() != 12) {
                continue;
            }
            for (Element row : e.select("tr")) {
                Elements cols = row.select("td");
                if (cols.size() == 0) {
                    continue;
                }

                TemplateUnit u = new TemplateUnit();

                u.code = cols.get(0).text();
                u.abbr = cols.get(1).text().replace("~", "");
                try {
                    u.name = cols.get(5).text().replace("%", "");
                } catch (Exception ex) {
                    continue;
                }

                try {
                    u.pluralName = cols.get(6).text();
                    if (u.pluralName.isBlank()) {
                        u.pluralName = u.name + "s";
                    }
                } catch (Exception ex) {
                    u.pluralName = u.name + "s";
                }
                try {
                    u.link = cols.get(11).selectFirst("a").attr("href");
                } catch (Exception ex) {
                }
                units.put(u.code, u);
                System.out.println(Gson.toJson(u));
            }
        }

        PrintWriter out = FileUtils.getPrintWriter("resources/wikipedia_template_convert.json");
        out.println(Gson.toJson(units, true));
        out.close();
    }

    public static void main(String[] args) {
        crawl();
    }
}
