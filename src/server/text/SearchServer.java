package server.text;

import config.Configuration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import server.text.handler.EvaluateHandler;
import server.text.handler.TypeSuggestionHandler;
import server.text.handler.WikiImageHandler;
import server.text.handler.WikiViewHandler;
import server.text.handler.search.SearchHandler;
import server.text.handler.search.SocketSearchServlet;

public class SearchServer {
    public static final String RESOURCE_BASE = "./web/";
    public static final String SEARCH_PATH = "/search";
    public static final String SOCKET_SEARCH_PATH = "/search_socket";
    public static final String TYPE_SUGGESTION_PATH = "/type_suggest";
    public static final String EVALUATE_PATH = "/evaluate";
    public static final String WIKI_IMG_PATH = "/wikilink";
    public static final String WIKI_VIEW_PATH = "/wikiview";
    public static final int DEFAULT_PORT = Integer.parseInt(Configuration.get("search.server.default-port"));

    // [DEV|PRODUCTION]
    public static void main(String[] args) throws Exception {
        Server server = new Server(new QueuedThreadPool(32, 16));

        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory());
        connector.setPort(DEFAULT_PORT);
        connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration().setRequestHeaderSize(1024 * 1024);
        server.addConnector(connector);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);

        boolean dev = (args.length > 0 && args[0].equals("DEV"));

        resourceHandler.setWelcomeFiles(new String[]{dev ? "index_dev.html" : "index.html"});
        resourceHandler.setResourceBase(RESOURCE_BASE);

        ServletContextHandler searchSocketHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        searchSocketHandler.addServlet(SocketSearchServlet.class, SOCKET_SEARCH_PATH);

        ContextHandler searchHandler = new ContextHandler();
        searchHandler.setContextPath(SEARCH_PATH);
        searchHandler.setHandler(new SearchHandler(dev ? 10 : 20));

        ContextHandler wikiImgHandler = new ContextHandler();
        wikiImgHandler.setContextPath(WIKI_IMG_PATH);
        wikiImgHandler.setHandler(new WikiImageHandler());

        ContextHandler wikiViewHandler = new ContextHandler();
        wikiViewHandler.setContextPath(WIKI_VIEW_PATH);
        wikiViewHandler.setHandler(new WikiViewHandler());

        ContextHandler typeSuggestionHandler = new ContextHandler();
        typeSuggestionHandler.setContextPath(TYPE_SUGGESTION_PATH);
        typeSuggestionHandler.setHandler(new TypeSuggestionHandler(10));

        HandlerList handlers = new HandlerList();
        if (!dev) {
            handlers.setHandlers(new Handler[]{resourceHandler, typeSuggestionHandler, searchHandler, wikiImgHandler, wikiViewHandler, searchSocketHandler, new DefaultHandler()});
        } else {
            ContextHandler evaluateHandler = new ContextHandler();
            evaluateHandler.setContextPath(EVALUATE_PATH);
            evaluateHandler.setHandler(new EvaluateHandler("./exp_2/"));
            handlers.setHandlers(new Handler[]{resourceHandler, searchHandler, evaluateHandler, new DefaultHandler()});
        }

        server.setHandler(handlers);
        server.start();
        server.join();
    }
}
