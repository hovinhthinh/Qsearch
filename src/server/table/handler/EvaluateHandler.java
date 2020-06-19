package server.table.handler;

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
            Reader in = request.getReader();
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024 * 8];
            int c;
            while ((c = in.read(buffer)) != -1) {
                builder.append(buffer, 0, c);
            }
            SearchResult evalResult;
            evalResult = Gson.fromJson(builder.toString(), SearchResult.class);

            File saveFile = new File(SAVE_PATH, evalResult.evalDomain + "_" + evalResult.encode());
            LOGGER.info("Logging: " + saveFile.getName());

            boolean overwrite = false;
            if (saveFile.exists()) {
                overwrite = true;
            }

            PrintWriter out = FileUtils.getPrintWriter(saveFile, Charset.forName("UTF-8"));
            out.println(Gson.toJson(evalResult));
            out.close();

            response.put("verdict", overwrite ? "OVERWRITE" : "OK");
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