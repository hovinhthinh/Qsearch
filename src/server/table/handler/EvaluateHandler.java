package server.table.handler;

import org.json.JSONArray;
import org.json.JSONObject;
import server.table.handler.search.SearchResult;
import util.FileUtils;
import util.Gson;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class EvaluateHandler extends HttpServlet {
    public static final Logger LOGGER = Logger.getLogger(EvaluateHandler.class.getName());
    public static final String SAVE_PATH = "eval/table/exp_2/annotation";

    public EvaluateHandler() {
        new File(SAVE_PATH).mkdirs();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        try {
            boolean delete = request.getParameter("action") != null && request.getParameter("action").equals("delete");

            Reader in = request.getReader();
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024 * 8];
            int c;
            while ((c = in.read(buffer)) != -1) {
                builder.append(buffer, 0, c);
            }
            SearchResult evalResult = Gson.fromJson(builder.toString(), SearchResult.class);

            File saveFile = new File(SAVE_PATH, evalResult.evalDomain + "_" + evalResult.encode());
            if (delete) {
                LOGGER.info("Deleting: " + saveFile.getName());
                if (saveFile.exists()) {
                    if (saveFile.delete()) {
                        response.put("verdict", "DELETE");
                    } else {
                        throw new Exception("Cannot delete file.");
                    }
                } else {
                    response.put("verdict", "NOT_EXIST");
                }
            } else {
                LOGGER.info("Saving: " + saveFile.getName());
                boolean overwrite = false;
                if (saveFile.exists()) {
                    overwrite = true;
                }
                PrintWriter out = FileUtils.getPrintWriter(saveFile, Charset.forName("UTF-8"));
                out.println(Gson.toJson(evalResult));
                out.close();
                response.put("verdict", overwrite ? "OVERWRITE" : "OK");
            }
        } catch (Exception e) {
            try {
                response.put("verdict", "Unknown error occurred.");
            } catch (Exception ep) {
            }
        }
        httpServletResponse.getWriter().print(response.toString());
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        try {
            JSONArray data = new JSONArray();
            for (File f : new File(SAVE_PATH).listFiles()) {
                data.put(new JSONObject(FileUtils.getContent(f, StandardCharsets.UTF_8)));
            }
            response.put("verdict", "OK");
            response.put("data", data);
        } catch (Exception e) {
            try {
                response.put("verdict", "Unknown error occurred.");
            } catch (Exception ep) {
            }
        }
        httpServletResponse.getWriter().print(response.toString());
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }
}
