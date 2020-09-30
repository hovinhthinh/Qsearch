package server.common.handler;

import org.json.JSONArray;
import org.json.JSONObject;
import util.Crawler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.util.concurrent.ConcurrentHashMap;

public class WikiViewHandler extends HttpServlet {
    private static final String URL_TEMPLATE = "https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/en.wikipedia/all-access/all-agents/{ENTITY}/monthly/20190101/20191231";
    private static final Proxy p = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("dmz-gw.mpi-klsb.mpg.de", 3128));
    private static final boolean USE_PROXY = false;

    private static ConcurrentHashMap<String, Integer> CACHE = new ConcurrentHashMap<>();

    // returns: -2: article not found; -1: unknown error (e.g. network error, timeout, ...)
    public static int getView(String entity) {
        Integer v = CACHE.get(entity);
        if (v != null) {
            return v;
        }
        try {
            String url = URL_TEMPLATE.replace("{ENTITY}", URLEncoder.encode(entity.substring(1, entity.length() - 1), "UTF-8"));
            String content = Crawler.getContentFromUrl(url, null, "GET", null, USE_PROXY ? p : null, true);

            JSONObject json = new JSONObject(content);
            if (json.has("title") && json.getString("title").equals("Not found.")) {
                return -2;
            }

            JSONArray monthly = json.getJSONArray("items");
            v = 0;
            for (int i = 0; i < monthly.length(); ++i) {
                v += monthly.getJSONObject(i).getInt("views");
            }
            CACHE.put(entity, v);
            return v;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        httpServletResponse.setCharacterEncoding("utf-8");
        String entity = request.getParameter("entity");
        JSONObject response = new JSONObject().put("view", getView(entity));
        httpServletResponse.getWriter().print(response.toString());
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    public static void main(String[] args) {
        System.out.println(getView("<Barack_Obama>"));
    }
}
