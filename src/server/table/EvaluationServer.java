package server.table;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import server.table.handler.EvaluateHandler;
import server.table.handler.GetEvaluationInfoHandler;
import server.table.handler.search.SearchHandler;

import java.util.Arrays;
import java.util.List;

@Deprecated
public class EvaluationServer {
    public static final String RESOURCE_BASE = "./web/";
    public static final String SEARCH_PATH = "/search";
    public static final String EVALUATE_PATH = "/evaluate";
    public static final String INFO_PATH = "/info";

    public static final String SAVE_BASE_PATH = "./exp_2/";

    public static List<String> DOMAINS = Arrays.asList("FINANCE", "SPORTS", "TRANSPORT", "GEOMIS", "TECHNOLOGY");
    public static List<String> MODELS = Arrays.asList("EMBEDDING", "KL");
    public static List<String> EVALUATORS = Arrays.asList("EVALUATOR_1", "EVALUATOR_2", "EVALUATOR_3");

    // [PORT] [DOMAIN] [MODEL] [INPUTS] [EVALUATOR]
    public static void main(String[] args) throws Exception {
//        args = "1209 FINANCE EMBEDDING ./exp_2/inputs/sample.txt EVALUATOR_1".split(" ");

        if (!DOMAINS.contains(args[1]) || !MODELS.contains(args[2]) || !EVALUATORS.contains(args[4])) {
            throw new Exception("Invalid DOMAIN or MODEL.");
        }

        Server server = new Server(new QueuedThreadPool(32, 8));
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory());
        connector.setPort(Integer.parseInt(args[0]));
        server.addConnector(connector);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);

        resourceHandler.setWelcomeFiles(new String[]{"index_evaluation.html"});
        resourceHandler.setResourceBase(RESOURCE_BASE);

        ContextHandler searchHandler = new ContextHandler();
        searchHandler.setContextPath(SEARCH_PATH);
        searchHandler.setHandler(new SearchHandler(10));

        ContextHandler infoHandler = new ContextHandler();
        infoHandler.setContextPath(INFO_PATH);
        infoHandler.setHandler(new GetEvaluationInfoHandler(args[1], args[2], args[3], args[4]));

        ContextHandler evaluateHandler = new ContextHandler();
        evaluateHandler.setContextPath(EVALUATE_PATH);
        evaluateHandler.setHandler(new EvaluateHandler(SAVE_BASE_PATH + "/" + args[4]));

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{resourceHandler, searchHandler, infoHandler, evaluateHandler,
                new DefaultHandler()});

        server.setHandler(handlers);
        server.start();
        server.join();
    }
}
