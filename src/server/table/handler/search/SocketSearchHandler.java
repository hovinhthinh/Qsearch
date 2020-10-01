package server.table.handler.search;

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
                additionalParams.put("corpus", o.getString("corpus"));
            }

            if (o.has("linking-threshold")) {
                additionalParams.put("linking-threshold", Double.parseDouble(o.getString("linking-threshold")));
            }

            boolean performConsistencyRescoring = o.has("rescore") && o.getString("rescore").equals("true");

            additionalParams.put("session", session);

            // locational params:
            if (o.has("HEADER_MATCH_WEIGHT")) {
                additionalParams.put("HEADER_MATCH_WEIGHT", Double.parseDouble(o.getString("HEADER_MATCH_WEIGHT")));
            }
            if (o.has("CAPTION_MATCH_WEIGHT")) {
                additionalParams.put("CAPTION_MATCH_WEIGHT", Double.parseDouble(o.getString("CAPTION_MATCH_WEIGHT")));
            }
            if (o.has("TITLE_MATCH_WEIGHT")) {
                additionalParams.put("TITLE_MATCH_WEIGHT", Double.parseDouble(o.getString("TITLE_MATCH_WEIGHT")));
            }
            if (o.has("SAME_ROW_MATCH_WEIGHT")) {
                additionalParams.put("SAME_ROW_MATCH_WEIGHT", Double.parseDouble(o.getString("SAME_ROW_MATCH_WEIGHT")));
            }
            if (o.has("RELATED_TEXT_MATCH_WEIGHT")) {
                additionalParams.put("RELATED_TEXT_MATCH_WEIGHT", Double.parseDouble(o.getString("RELATED_TEXT_MATCH_WEIGHT")));
            }

            if (o.has("QUANTITY_MATCH_WEIGHT")) {
                additionalParams.put("QUANTITY_MATCH_WEIGHT", Double.parseDouble(o.getString("QUANTITY_MATCH_WEIGHT")));
            }

            if (o.has("ENTITY_POPULARITY_WEIGHT")) {
                additionalParams.put("ENTITY_POPULARITY_WEIGHT", Double.parseDouble(o.getString("ENTITY_POPULARITY_WEIGHT")));
            }

            // consistency params:
            if (o.has("HEADER_TF_WEIGHT")) {
                additionalParams.put("HEADER_TF_WEIGHT", Double.parseDouble(o.getString("HEADER_TF_WEIGHT")));
            }
            if (o.has("CAPTION_TF_WEIGHT")) {
                additionalParams.put("CAPTION_TF_WEIGHT", Double.parseDouble(o.getString("CAPTION_TF_WEIGHT")));
            }
            if (o.has("TITLE_TF_WEIGHT")) {
                additionalParams.put("TITLE_TF_WEIGHT", Double.parseDouble(o.getString("TITLE_TF_WEIGHT")));
            }
            if (o.has("SAME_ROW_TF_WEIGHT")) {
                additionalParams.put("SAME_ROW_TF_WEIGHT", Double.parseDouble(o.getString("SAME_ROW_TF_WEIGHT")));
            }
            if (o.has("RELATED_TEXT_TF_WEIGHT")) {
                additionalParams.put("RELATED_TEXT_TF_WEIGHT", Double.parseDouble(o.getString("RELATED_TEXT_TF_WEIGHT")));
            }
            if (o.has("QUANTITY_FEATURE_BOOST")) {
                additionalParams.put("QUANTITY_FEATURE_BOOST", Double.parseDouble(o.getString("QUANTITY_FEATURE_BOOST")));
            }

            if (o.has("CONSISTENCY_LEARNING_N_FOLD")) {
                additionalParams.put("CONSISTENCY_LEARNING_N_FOLD", Integer.parseInt(o.getString("CONSISTENCY_LEARNING_N_FOLD")));
            }
            if (o.has("CONSISTENCY_LEARNING_PROBE_RATE")) {
                additionalParams.put("CONSISTENCY_LEARNING_PROBE_RATE", Double.parseDouble(o.getString("CONSISTENCY_LEARNING_PROBE_RATE")));
            }
            if (o.has("KNN_ESTIMATOR_K")) {
                additionalParams.put("KNN_ESTIMATOR_K", Integer.parseInt(o.getString("KNN_ESTIMATOR_K")));
            }
            if (o.has("INTERPOLATION_WEIGHT")) {
                additionalParams.put("INTERPOLATION_WEIGHT", Double.parseDouble(o.getString("INTERPOLATION_WEIGHT")));
            }
            // end

            int nResult = o.has("ntop") ? Integer.parseInt(o.getString("ntop")) : 10;

            String sessionKey = SearchHandler.search(nResult, fullConstraint,
                    typeConstraint, contextConstraint, quantityConstraint, performConsistencyRescoring, additionalParams, null);

            try {
                session.getRemote().sendString(new JSONObject().put("s", sessionKey).toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            session.close(200, "OK");
        }
    }
}
