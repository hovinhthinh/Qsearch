package server.text.handler;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import util.FileUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class GetEvaluationInfoHandler extends AbstractHandler {

    public String domain, model, inputFilePath, evaluator;

    public GetEvaluationInfoHandler(String domain, String model, String inputFilePath, String evaluator) {
        this.domain = domain;
        this.model = model;
        this.inputFilePath = inputFilePath;
        this.evaluator = evaluator;
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse) throws IOException, ServletException {
        request.setHandled(true);

        httpServletResponse.setCharacterEncoding("utf-8");
        JSONObject response = new JSONObject();
        try {
            response.put("domain", domain);
            response.put("model", model);
            response.put("evaluator", evaluator);
            JSONArray array = new JSONArray();
            for (String line : FileUtils.getLineStream(inputFilePath, "UTF-8")) {
                String[] arr = line.split("\t");
                if (arr.length == 4) {
                    JSONObject o = new JSONObject();
                    o.put("full", arr[0]);
                    o.put("type", arr[1]);
                    o.put("context", arr[2]);
                    o.put("quantity", arr[3]);
                    array.put(o);
                }
            }
            response.put("data", array);
            httpServletResponse.getWriter().print(response.toString());
        } catch (Exception e) {
        }
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    }
}
