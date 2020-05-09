package server.table.experimental;

import config.Configuration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

@Deprecated
public class ExplorerServer {
    public static final String ENTITY_PATH = "/entity";
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

//        ContextHandler wikiImgHandler = new ContextHandler();
//        wikiImgHandler.setContextPath(WIKI_IMG_PATH);
//        wikiImgHandler.setHandler(new WikiImageHandler());

//        ContextHandler typeSuggestionHandler = new ContextHandler();
//        typeSuggestionHandler.setContextPath(TYPE_SUGGESTION_PATH);
//        TypeSuggestionHandler.load(10);
//        typeSuggestionHandler.setHandler(new TypeSuggestionHandler(10));

//        ContextHandler entityHandler = new ContextHandler();
//        entityHandler.setContextPath(ENTITY_PATH);
//        entityHandler.setHandler(new EntityQfactHandler());

        // JSP
        WebAppContext ctx = new WebAppContext();
        ctx.setContextPath("/");
        ctx.setResourceBase("./web");
        ctx.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*/[^/]*jstl.*\\.jar$");
        org.eclipse.jetty.webapp.Configuration.ClassList classlist = org.eclipse.jetty.webapp.Configuration.ClassList.setServerDefault(server);
        classlist.addAfter("org.eclipse.jetty.webapp.FragmentConfiguration", "org.eclipse.jetty.plus.webapp.EnvConfiguration", "org.eclipse.jetty.plus.webapp.PlusConfiguration");
        classlist.addBefore("org.eclipse.jetty.webapp.JettyWebXmlConfiguration", "org.eclipse.jetty.annotations.AnnotationConfiguration");

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{
                ctx,
        });

        server.setHandler(handlers);
        server.start();
        server.join();
    }
}
