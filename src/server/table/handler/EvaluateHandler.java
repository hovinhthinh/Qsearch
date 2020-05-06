package server.table.handler;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONObject;
import server.table.handler.search.SearchResult;
import util.Gson;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.logging.Logger;

@Deprecated
public class EvaluateHandler extends AbstractHandler {
    public static final Logger LOGGER = Logger.getLogger(EvaluateHandler.class.getName());
    private String savePath;

    public EvaluateHandler(String savePath) {
        this.savePath = savePath;
        new File(savePath).mkdirs();
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException, ServletException {
        request.setHandled(true);
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

            File saveFile = new File(savePath,
                    evalResult.evalDomain + "_" + evalResult.matchingModel + "_" + evalResult.encode());
            LOGGER.info("Logging: " + saveFile.getName());

            boolean overwrite = false;
            if (saveFile.exists()) {
                overwrite = true;
            }

            PrintWriter out = new PrintWriter(saveFile);
            out.println(Gson.toJson(evalResult));

            out.close();
            response.put("verdict", overwrite ? "OVERWRITE" : "OK");
        } catch (Exception e) {
            try {
                response.put("verdict", "Unknown error occured.");
            } catch (Exception ep) {
            }
        }
        httpServletResponse.getWriter().print(response.toString());
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }

}
