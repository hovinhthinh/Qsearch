package server.common.handler;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import util.Crawler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.ConcurrentHashMap;

public class WikiImageHandler extends HttpServlet {
    private static final Proxy p = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("dmz-gw.mpi-klsb.mpg.de", 3128));
    private static final boolean USE_PROXY = false;

    private static ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    public static String getImageLink(String wikiLink) {
        String link = CACHE.get(wikiLink);
        if (link != null) {
            return link;
        }
        try {
            Document doc = Jsoup.parse(Crawler.getContentFromUrl(wikiLink, USE_PROXY ? p : null));
            Element infobox = doc.selectFirst(".infobox .image img");
            link = (infobox != null) ? infobox.attr("src") : "";
            CACHE.put(wikiLink, link);
            return link;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        httpServletResponse.setCharacterEncoding("utf-8");
        JSONObject response = new JSONObject();
        String wikiLink = request.getParameter("link");
        try {
            String link = getImageLink(wikiLink);
            if (link != null) {
                response.put("imgLink", getImageLink(wikiLink));
            }
        } catch (Exception e) {
        }
        httpServletResponse.getWriter().print(response.toString());
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

}
