package server.table.handler.search;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.json.JSONObject;
import util.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SocketSearchAdapter extends WebSocketAdapter {
    public static final Logger LOGGER = Logger.getLogger(SocketSearchAdapter.class.getName());
    private Session session;

    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);
        this.session = session;
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        super.onWebSocketClose(statusCode, reason);
        this.session = null;
    }

    // queryDescription is a json consisting of following fields:
    // 'full' : <fullQuery>, 'model': 'KL|EMBEDDING'
    @Override
    public void onWebSocketText(String queryDescription) {
        LOGGER.info(queryDescription);
        JSONObject o = new JSONObject(queryDescription);
        // Get parameters
        String fullConstraint = o.getString("full");

        Map additionalParams = new HashMap();

        if (o.has("corpus")) {
            additionalParams.put("corpus", o.getString("corpus")); // ANY || STICS || NYT
        }

        additionalParams.put("session", session);

        int nResult = o.has("ntop") ? Integer.parseInt(o.getString("ntop")) : 20;

        SearchResult response = SearchHandler.search(nResult, fullConstraint,
                null, null, null, additionalParams);

        String responseStr = Gson.toJson(response);

        try {
            session.getRemote().sendString(responseStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        session.close(200, "OK");
    }
}