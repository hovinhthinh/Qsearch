package server.table.handler.search;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.json.JSONObject;

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

    @Override
    public void onWebSocketText(String queryDescription) {
        LOGGER.info(queryDescription);
        JSONObject o = new JSONObject(queryDescription);
        // Get parameters
        String typeConstraint = o.has("type") ? o.getString("type") : null;
        String contextConstraint = o.has("context") ? o.getString("context") : null;
        String quantityConstraint = o.has("quantity") ? o.getString("quantity") : null;
        String fullConstraint = o.has("full") ? o.getString("full") : null;

        Map additionalParams = new HashMap();

        if (o.has("corpus")) {
            additionalParams.put("corpus", o.getString("corpus"));
        }

        if (o.has("linking-threshold")) {
            additionalParams.put("linking-threshold", Float.parseFloat(o.getString("linking-threshold")));
        }

        additionalParams.put("session", session);

        int nResult = o.has("ntop") ? Integer.parseInt(o.getString("ntop")) : 10;

        String sessionKey = SearchHandler.search(nResult, fullConstraint,
                typeConstraint, contextConstraint, quantityConstraint, additionalParams);

        try {
            session.getRemote().sendString(new JSONObject().put("s", sessionKey).toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        session.close(200, "OK");
    }
}