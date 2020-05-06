package storage.table.index;

import org.json.JSONException;
import org.json.JSONObject;
import util.Gson;
import util.distributed.String2StringMap;

import java.util.Arrays;
import java.util.List;

public class WIKIPEDIA extends String2StringMap {
    public WIKIPEDIA() {
        data.wikipedia.WIKIPEDIA.PARSE_ENTITY = false;
    }

    @Override
    public List<String> map(String input) {
        try {
            JSONObject o = new JSONObject(input);
            TableIndex tableIndex = new TableIndex();
            tableIndex.table = data.wikipedia.WIKIPEDIA.parseFromJSON(o);
            if (tableIndex.table == null) {
                return null;
            }
            tableIndex.caption = o.has("tableCaption") ? o.getString("tableCaption") : "";
            tableIndex.pageTitle = o.has("pgTitle") ? o.getString("pgTitle") : "";
            if (o.has("sectionTitles")) {
                if (o.has("pgTitle")) {
                    tableIndex.pageTitle += "\r\n";
                }
                tableIndex.pageTitle += o.getString("sectionTitles");
            }
            tableIndex.pageContent = o.has("sectionText") ? o.getString("sectionText") : "";
            tableIndex.tableText = tableIndex.table.getTableRawContentForSearch();
            return Arrays.asList(Gson.toJson(tableIndex));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

    }
}
