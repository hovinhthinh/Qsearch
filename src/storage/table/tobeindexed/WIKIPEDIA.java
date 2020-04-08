package storage.table.tobeindexed;

import com.google.gson.Gson;
import org.json.JSONException;
import org.json.JSONObject;
import storage.table.ElasticSearchTableImport;
import util.distributed.String2StringMap;

import java.util.Arrays;
import java.util.List;

public class WIKIPEDIA implements String2StringMap {
    Gson gson = new Gson();

    public WIKIPEDIA() {
        data.wikipedia.WIKIPEDIA.PARSE_ENTITY = false;
    }

    @Override
    public List<String> map(String input) {
        try {
            JSONObject o = new JSONObject(input);
            ElasticSearchTableImport.TableIndex tableIndex = new ElasticSearchTableImport.TableIndex();
            tableIndex.table = data.wikipedia.WIKIPEDIA.parseFromJSON(o);
            if (tableIndex.table == null) {
                return null;
            }
            tableIndex.caption = o.has("tableCaption") ? o.getString("tableCaption") : "";
            tableIndex.pageTitle = o.has("pgTitle") ? o.getString("pgTitle") : "";
            tableIndex.pageContent = o.has("sectionText") ? o.getString("sectionText") : "";
            tableIndex.tableText = tableIndex.table.getTableRawContentForSearch();
            return Arrays.asList(gson.toJson(tableIndex));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

    }
}
