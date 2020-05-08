package storage.table.index;

import model.table.Table;
import org.json.JSONObject;
import util.Gson;

// For unparsed tables (without any annotation).
public class TableIndex {
    public Table table;
    public String pageContent, caption, pageTitle, sectionTitles, tableText;

    public String toESBulkContent() {
        return new JSONObject()
                .put("pageContent", pageContent)
                .put("caption", caption)
                .put("pageTitle", pageTitle)
                .put("sectionTitles", sectionTitles)
                .put("tableText", tableText)
                .put("json", Gson.toJson(table))
                .toString();
    }
}