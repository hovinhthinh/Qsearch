package storage.text.migrate;

import net.openhft.chronicle.map.ChronicleMap;
import org.json.JSONArray;
import org.json.JSONObject;
import storage.text.ElasticSearchDataImport;
import util.HTTPRequest;
import util.SelfMonitor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ChronicleMapQfactStorage {
    public static final String QFACT_INDEX_FILE = "/GW/D5data-13/hvthinh/Qsearch/qfact.index";

    private static ChronicleMap<String, String> INDEX = null;

    public static ArrayList<String> SEARCHABLE_ENTITIES = null;

    static {
        try {
            INDEX = ChronicleMap
                    .of(String.class, String.class)
                    .averageKeySize(30)
                    .averageValueSize(7000)
                    .actualSegments(32)
                    .entriesPerSegment(45000)
                    .createPersistedTo(new File(QFACT_INDEX_FILE));
            SEARCHABLE_ENTITIES = new ArrayList<>();
            INDEX.forEachEntry(e -> {
                SEARCHABLE_ENTITIES.add(e.key().get());
            });
            SEARCHABLE_ENTITIES.trimToSize();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void migrateElasticSearchStorageToChronicleMap() {
        INDEX.clear();
        try {
            String url = ElasticSearchDataImport.PROTOCOL + "://" + ElasticSearchDataImport.ES_HOST + "/" + ElasticSearchDataImport.INDEX + "/" + ElasticSearchDataImport.TYPE + "/_search?scroll=15m";
            String body = "{\"size\":1000,\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"searchable\"}}]}}}";
            String data = HTTPRequest.POST(url, body);
            if (data == null) {
                throw new RuntimeException("file not found.");
            }
            JSONObject json = new JSONObject(data);
            String scroll_id = json.getString("_scroll_id");

            json = json.getJSONObject("hits");
            SelfMonitor m = new SelfMonitor(null, json.getInt("total"), 10);
            m.start();

            JSONArray arr = json.getJSONArray("hits");
            for (int i = 0; i < arr.length(); ++i) {
                JSONObject entityFacts = arr.getJSONObject(i);
                String entity = entityFacts.getString("_id");
                JSONArray facts = entityFacts.getJSONObject("_source").getJSONArray("facts");
                String value = facts.toString();
                INDEX.put(entity, value);
                m.incAndGet();
            }

            url = ElasticSearchDataImport.PROTOCOL + "://" + ElasticSearchDataImport.ES_HOST + "/_search/scroll";
            body = "{\"scroll\":\"15m\",\"scroll_id\":\"" + scroll_id + "\"}";
            do {
                data = HTTPRequest.POST(url, body);
                if (data == null) {
                    throw new RuntimeException("scroll_id expired.");
                }
                json = new JSONObject(data);
                arr = json.getJSONObject("hits").getJSONArray("hits");
                for (int i = 0; i < arr.length(); ++i) {
                    JSONObject entityFacts = arr.getJSONObject(i);
                    String entity = entityFacts.getString("_id");
                    JSONArray facts = entityFacts.getJSONObject("_source").getJSONArray("facts");
                    String value = facts.toString();
                    INDEX.put(entity, value);
                    m.incAndGet();
                }
            } while (arr.length() > 0);

            m.forceShutdown();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("unknown exception.");
        }
        INDEX.close();
    }

    public static JSONArray get(String entity) {
        return new JSONArray(INDEX.get(entity));
    }

    public static void main(String[] args) {
//        migrateElasticSearchStorageToChronicleMap();
    }
}
