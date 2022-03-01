package quid;

import net.openhft.chronicle.map.ChronicleMap;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.json.JSONObject;
import util.FileUtils;
import util.Gson;
import util.ObjectCompressor;
import util.SelfMonitor;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

public class DocumentIndexer extends AbstractHandler {
    public static final String DOCUMENT_INDEX_FILE = "/GW/D5data-14/hvthinh/quid/wikipedia.index";
//    public static final String DOCUMENT_INDEX_FILE = " /dev/shm/wikipedia.index";

    private String indexFile;
    private ChronicleMap<String, byte[]> index = null;

    public DocumentIndexer(String indexFile) {
        this.indexFile = indexFile;
    }

    public DocumentIndexer() {
        this(DOCUMENT_INDEX_FILE);
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        baseRequest.setHandled(true);
        String path = request.getPathInfo();
        if (path.equals("/get")) {
            String content = get(request.getParameter("id"));
            if (content == null) {
                return;
            }
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(content);

        } else if (path.equals("/ids")) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);

            response.getWriter().println(Gson.toJson(new ArrayList<>(keySet())));
        }
    }

    public void load() {
        try {
            index = ChronicleMap
                    .of(String.class, byte[].class)
                    .averageKeySize(32)
                    .averageValueSize(2048)
                    .entries(6000000)
                    .createPersistedTo(new File(this.indexFile));
            System.out.println("Index size: " + index.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void create() {
        load();
        System.out.println("Clearing index");
        index.clear();
    }

    public String get(String id) {
        return ObjectCompressor.decompressByteArrayIntoString(index.get(id));
    }

    public void add(String id, String content) {
        index.put(id, ObjectCompressor.compressStringIntoByteArray(content));
    }

    public Set<String> keySet() {
        return index.keySet();
    }

    public static void indexWikipedia() {
        DocumentIndexer indexer = new DocumentIndexer();
        indexer.create();
        SelfMonitor m = new SelfMonitor(null, -1, 10);
        m.start();
        for (String line : FileUtils.getLineStream("/GW/D5data-14/hvthinh/enwiki-09-2021/standardized.json.gz", "UTF-8")) {
            Document d = new Document();
            JSONObject o = new JSONObject(line);

            d.content = o.getString("content");
            d.id = "WIKI:" + o.getString("title");
            d.source = o.getString("source");

            indexer.add(d.id, Gson.toJson(d));
            m.incAndGet();
        }
        m.forceShutdown();
    }

    public static void startServer(int port, int nThreads) throws Exception {
        ThreadPool threadPool = new QueuedThreadPool(nThreads, nThreads, nThreads);
        Server server = new Server(threadPool);

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.setConnectors(new Connector[]{connector});

        DocumentIndexer indexer = new DocumentIndexer();
        indexer.load();
        server.setHandler(indexer);
        server.start();
        server.join();
    }

    public static void main(String[] args) throws Exception {
        startServer(10000, 64);
    }
}
