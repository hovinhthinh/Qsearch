package server.table;

import config.Configuration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import server.table.handler.search.SearchHandler;

public class SearchServer {
    public static final String RESOURCE_BASE = "./web/";
    public static final String SEARCH_PATH = "/search";
    public static final String TYPE_SUGGESTION_PATH = "/type_suggest";
    public static final String EVALUATE_PATH = "/evaluate";
    public static final int DEFAULT_PORT = Integer.parseInt(Configuration.get("search.server.default-port"));

    // [DEV|PRODUCTION]
    public static void main(String[] args) throws Exception {
        args = "DEV".split(" ");
        Server server = new Server(new QueuedThreadPool(32, 16));

        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory());
        connector.setPort(DEFAULT_PORT);
        connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration().setRequestHeaderSize(1024 * 1024);
        server.addConnector(connector);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);

        boolean dev = (args.length > 0 && args[0].equals("DEV"));

        resourceHandler.setWelcomeFiles(new String[]{dev ? "table/index_dev.html" : "table/index.html"});
        resourceHandler.setResourceBase(RESOURCE_BASE);

        ContextHandler searchHandler = new ContextHandler();
        searchHandler.setContextPath(SEARCH_PATH);
        searchHandler.setHandler(new SearchHandler(dev ? 10 : 20));

//        ContextHandler typeSuggestionHandler = new ContextHandler();
//        typeSuggestionHandler.setContextPath(TYPE_SUGGESTION_PATH);
//        typeSuggestionHandler.setHandler(new TypeSuggestionHandler(10));

        HandlerList handlers = new HandlerList();
        if (!dev) {
//            handlers.setHandlers(new Handler[]{resourceHandler, typeSuggestionHandler, searchHandler, new DefaultHandler()});
        } else {
//            ContextHandler evaluateHandler = new ContextHandler();
//            evaluateHandler.setContextPath(EVALUATE_PATH);
//            evaluateHandler.setHandler(new EvaluateHandler("./exp_2/"));
//            handlers.setHandlers(new Handler[]{resourceHandler, searchHandler, evaluateHandler, new DefaultHandler()});
            handlers.setHandlers(new Handler[]{resourceHandler, searchHandler, new DefaultHandler()});
        }

        server.setHandler(handlers);
        server.start();
        server.join();
    }
}
