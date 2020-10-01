package server.text.handler.search;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SocketSearchHandler extends WebSocketServlet {

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.register(SocketSearchAdapter.class);
    }

    public static class SocketSearchAdapter extends WebSocketAdapter {
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
            LOGGER.info("FROM: " + session.getRemoteAddress().getAddress()
                    + "  X-Forwarded-For: " + session.getUpgradeRequest().getHeader("X-Forwarded-For")
                    + "  | " + queryDescription);
            JSONObject o = new JSONObject(queryDescription);
            // Get parameters
            String typeConstraint = o.has("type") ? o.getString("type") : null;
            String contextConstraint = o.has("context") ? o.getString("context") : null;
            String quantityConstraint = o.has("quantity") ? o.getString("quantity") : null;
            String fullConstraint = o.has("full") ? o.getString("full") : null;

            Map additionalParams = new HashMap();

            if (o.has("corpus")) {
                additionalParams.put("corpus", o.getString("corpus")); // ANY || STICS || NYT
            }
            if (o.has("model")) {
                additionalParams.put("model", o.getString("model")); // EMBEDDING || KL
            }
            if (o.has("alpha")) {
                additionalParams.put("alpha", Double.parseDouble(o.getString("alpha")));
            }
            if (o.has("lambda")) {
                additionalParams.put("lambda", Double.parseDouble(o.getString("lambda")));
            }

            additionalParams.put("session", session);

            int nResult = o.has("ntop") ? Integer.parseInt(o.getString("ntop")) : 20;
            String sessionKey = SearchHandler.search(null, nResult, fullConstraint,
                    typeConstraint, contextConstraint, quantityConstraint, additionalParams, null);
            try {
                session.getRemote().sendString(new JSONObject().put("s", sessionKey).toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            session.close(200, "OK");
        }
    }
}

