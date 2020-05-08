package storage.table.index;

import nlp.NLP;
import org.json.JSONException;
import org.json.JSONObject;
import util.Gson;
import util.distributed.String2StringMap;

import java.util.Arrays;
import java.util.List;

public class WIKIPEDIA extends String2StringMap {
    public WIKIPEDIA() {
        data.table.wikipedia.WIKIPEDIA.PARSE_ENTITY = false;
    }

    @Override
    public List<String> map(String input) {
        try {
            JSONObject o = new JSONObject(input);
            TableIndex tableIndex = new TableIndex();
            tableIndex.table = data.table.wikipedia.WIKIPEDIA.parseFromJSON(o);
            if (tableIndex.table == null) {
                return null;
            }
            tableIndex.caption = String.join(" ", NLP.tokenize(
                    o.has("tableCaption") ? o.getString("tableCaption") : "")
            );
            tableIndex.pageTitle = String.join(" ", NLP.tokenize(
                    o.has("pgTitle") ? o.getString("pgTitle") : "")
            );
            StringBuilder sb = new StringBuilder();
            if (o.has("sectionTitles")) {
                for (String l : o.getString("sectionTitles").split("\r\n")) {
                    if (sb.length() > 0) {
                        sb.append("\r\n");
                    }
                    sb.append(String.join(" ", NLP.tokenize(l)));
                }
            }
            tableIndex.sectionTitles = sb.toString();
            tableIndex.pageContent = String.join(" ", NLP.tokenize(
                    o.has("sectionText") ? o.getString("sectionText") : "")
            );

            tableIndex.tableText = tableIndex.table.getTableRawContentForSearch();
            return Arrays.asList(Gson.toJson(tableIndex));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

    }
}
