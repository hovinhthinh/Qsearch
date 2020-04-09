package storage.table.index;

import com.google.gson.Gson;
import model.table.Table;
import org.json.JSONObject;

// For unparsed tables (without any annotation).
public class TableIndex {
    private static final Gson GSON = new Gson();
    public Table table;
    public String pageContent, caption, pageTitle, tableText;

    public String toESBulkContent() {
        return new JSONObject()
                .put("pageContent", pageContent)
                .put("caption", caption)
                .put("pageTitle", pageTitle)
                .put("tableText", tableText)
                .put("json", GSON.toJson(table))
                .toString();
    }
}