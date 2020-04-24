package storage.table.index;

import com.google.gson.Gson;
import org.json.JSONException;
import org.json.JSONObject;
import util.distributed.String2StringMap;

import java.util.Arrays;
import java.util.List;

public class TABLEM extends String2StringMap {
    Gson gson = new Gson();

    @Override
    public List<String> map(String input) {
        try {
            JSONObject o = new JSONObject(input);
            TableIndex tableIndex = new TableIndex();
            tableIndex.table = data.tablem.TABLEM.parseFromJSON(o);
            if (tableIndex.table == null) {
                return null;
            }
            tableIndex.caption = o.has("title") ? o.getString("title") : "";
            tableIndex.pageTitle = o.has("pageTitle") ? o.getString("pageTitle") : "";
            tableIndex.pageContent = o.has("plainTextContent") ? o.getString("plainTextContent") : "";
            tableIndex.tableText = tableIndex.table.getTableRawContentForSearch();
            return Arrays.asList(gson.toJson(tableIndex));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
