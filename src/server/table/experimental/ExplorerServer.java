package server.table.experimental;

import config.Configuration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import server.table.experimental.gui.search.SearchHandler;
import server.table.experimental.gui.search.SocketSearchServlet;
import server.text.handler.WikiImageHandler;

@Deprecated
public class ExplorerServer {
    public static final String SEARCH_PATH = "/search";
    public static final String ENTITY_PATH = "/entity";
    public static final String SOCKET_SEARCH_PATH = "/search_socket";
    public static final String TYPE_SUGGESTION_PATH = "/type_suggest";
    public static final String WIKI_IMG_PATH = "/wikilink";
    public static final String WIKI_VIEW_PATH = "/wikiview";
    public static final int DEFAULT_PORT = Integer.parseInt(Configuration.get("search.server.default-port"));

    public static void main(String[] args) throws Exception {
        Server server = new Server(new QueuedThreadPool(32, 16));

        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory());
        connector.setPort(DEFAULT_PORT);
        connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration().setRequestHeaderSize(1024 * 1024);
        server.addConnector(connector);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);

        ServletContextHandler searchSocketHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        searchSocketHandler.addServlet(SocketSearchServlet.class, SOCKET_SEARCH_PATH);

        ContextHandler searchHandler = new ContextHandler();
        searchHandler.setContextPath(SEARCH_PATH);
        searchHandler.setHandler(new SearchHandler(10));

        ContextHandler wikiImgHandler = new ContextHandler();
        wikiImgHandler.setContextPath(WIKI_IMG_PATH);
        wikiImgHandler.setHandler(new WikiImageHandler());

//        ContextHandler typeSuggestionHandler = new ContextHandler();
//        typeSuggestionHandler.setContextPath(TYPE_SUGGESTION_PATH);
//        TypeSuggestionHandler.load(10);
//        typeSuggestionHandler.setHandler(new TypeSuggestionHandler(10));

//        ContextHandler entityHandler = new ContextHandler();
//        entityHandler.setContextPath(ENTITY_PATH);
//        entityHandler.setHandler(new EntityQfactHandler());

        WebAppContext ctx = new WebAppContext();
        ctx.setResourceBase("./web/");
        ctx.setContextPath("/");
        ctx.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*/[^/]*jstl.*\\.jar$");
        org.eclipse.jetty.webapp.Configuration.ClassList classlist = org.eclipse.jetty.webapp.Configuration.ClassList.setServerDefault(server);
        classlist.addAfter("org.eclipse.jetty.webapp.FragmentConfiguration", "org.eclipse.jetty.plus.webapp.EnvConfiguration", "org.eclipse.jetty.plus.webapp.PlusConfiguration");
        classlist.addBefore("org.eclipse.jetty.webapp.JettyWebXmlConfiguration", "org.eclipse.jetty.annotations.AnnotationConfiguration");

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{
                searchHandler,
                searchSocketHandler,
//                typeSuggestionHandler,
//                entityHandler,
                ctx});

        server.setHandler(handlers);
        server.start();
        server.join();
    }
}
