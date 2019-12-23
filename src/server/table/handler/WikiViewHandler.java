package server.table.handler;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONObject;
import util.Crawler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;

@Deprecated
public class WikiViewHandler extends AbstractHandler {
    private static final String URL_TEMPLATE = "https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/en.wikipedia/all-access/all-agents/{ENTITY}/monthly/2019070100/2019073100";
    private static final Proxy p = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("dmz-gw.mpi-klsb.mpg.de", 3128));
    private static final boolean USE_PROXY = false;

    public static int getView(String entity) {
        try {
            String url = URL_TEMPLATE.replace("{ENTITY}", URLEncoder.encode(entity, "UTF-8"));
            String content = Crawler.getContentFromUrl(url, USE_PROXY ? p : null);
            return new JSONObject(content).getJSONArray("items").getJSONObject(0).getInt("views");
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException, ServletException {
        request.setHandled(true);
        httpServletResponse.setCharacterEncoding("utf-8");
        String entity = request.getParameter("entity");
        if (entity.length() >= 2) {
            entity = entity.substring(1, entity.length() - 1);
        }
        JSONObject response = new JSONObject().put("view", getView(entity));
        httpServletResponse.getWriter().print(response.toString());
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static void main(String[] args) {
        System.out.println(getView("Barack_Obama"));
    }
}
