package server.common.handler;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang.RandomStringUtils;
import util.Gson;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class ResultCacheHandler extends HttpServlet {
    private static Cache<String, Object> CACHE = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .build();

    // return session key
    public static String addResult(Object r) {
        String key = RandomStringUtils.randomAlphanumeric(64);
        CACHE.put(key, r);
        return key;
    }

    public static Object getResultFromSession(String s) {
        return CACHE.getIfPresent(s);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        httpServletResponse.setCharacterEncoding("utf-8");
        Object r = getResultFromSession(request.getParameter("s"));
        if (r == null) {
            httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (r instanceof String) {
            httpServletResponse.getWriter().print(r);
        } else if (r instanceof server.text.handler.search.SearchResult) {
            try {
                server.text.handler.search.SearchResult result = (server.text.handler.search.SearchResult) r;

                result = (server.text.handler.search.SearchResult) result.clone(); // IMPORTANT!

                result.pageIdx = Integer.parseInt(request.getParameter("p"));
                result.startIdx = result.pageIdx * result.nResultsPerPage;
                result.topResults = new ArrayList<>(result.topResults.subList(
                        result.startIdx, Math.min(result.startIdx + result.nResultsPerPage, result.topResults.size())
                ));
                httpServletResponse.getWriter().print(Gson.toJson(result));
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
                httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

        } else if (r instanceof server.table.handler.search.SearchResult) {
            try {
                server.table.handler.search.SearchResult result = (server.table.handler.search.SearchResult) r;

                result = (server.table.handler.search.SearchResult) result.clone(); // IMPORTANT!

                result.pageIdx = Integer.parseInt(request.getParameter("p"));
                result.startIdx = result.pageIdx * result.nResultsPerPage;
                result.topResults = new ArrayList<>(result.topResults.subList(
                        result.startIdx, Math.min(result.startIdx + result.nResultsPerPage, result.topResults.size())
                ));
                result.populateTableIndexesFromTopResults();
                httpServletResponse.getWriter().print(Gson.toJson(result));
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
                httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

}
