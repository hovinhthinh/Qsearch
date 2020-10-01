package server.common.handler;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang.RandomStringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ResultCacheHandler extends HttpServlet {
    private static Cache<String, String> CACHE = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .build();

    // return session key
    public static String addResult(String r) {
        String key = RandomStringUtils.randomAlphanumeric(1024);
        CACHE.put(key, r);
        return key;
    }

    public static String getResultFromSession(String s) {
        return CACHE.getIfPresent(s);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        httpServletResponse.setCharacterEncoding("utf-8");
        String r = getResultFromSession(request.getParameter("s"));
        if (r != null) {
            httpServletResponse.getWriter().print(r);
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

}
