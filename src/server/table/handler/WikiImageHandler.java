package server.table.handler;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import util.Crawler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

@Deprecated
public class WikiImageHandler extends AbstractHandler {
    private static final Proxy p = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("dmz-gw.mpi-klsb.mpg.de", 3128));
    private static final boolean USE_PROXY = false;

    public static String getImageLink(String wikiLink) {
        try {
            Document doc = Jsoup.parse(Crawler.getContentFromUrl(wikiLink, USE_PROXY ? p : null));
            Element infobox = doc.selectFirst(".infobox .image img");
            String link = infobox.attr("src");
            return link;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException, ServletException {
        request.setHandled(true);
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
