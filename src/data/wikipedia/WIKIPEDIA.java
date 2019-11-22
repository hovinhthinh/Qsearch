package data.wikipedia;

import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import nlp.NLP;
import nlp.YagoType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;

// Read from TabEL (entity links are from original wikipedia)
// Entity Linking and Quantity Columns are already detected.
public class WIKIPEDIA {

    // Input is from "/GW/D5data/hvthinh/TabEL/tables.json.gz"
    private static Cell parseCellFromJSONObject(JSONObject json) {
        Cell cell = new Cell();
        cell.text = String.join(" ", NLP.tokenize(json.getString("text")));
        JSONArray links = json.getJSONArray("surfaceLinks");

        cell.entityLinks = new ArrayList<>();
        for (int i = 0; i < links.length(); ++i) {
            EntityLink el = new EntityLink();
            JSONObject linkI = links.getJSONObject(i);

            el.text = String.join(" ", NLP.tokenize(linkI.getString("surface")));
            String e = linkI.getJSONObject("target").getString("title");
            if (!YagoType.entityExists("<" + e + ">")) {
                continue;
            }
            el.target = "WIKIPEDIA:" + linkI.getString("linkType") + ":" + e;
            cell.entityLinks.add(el);
        }

        return cell;
    }

    public static Table parseFromJSON(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            Table table = new Table();

            table.nColumn = json.getInt("numCols");
            table.nHeaderRow = json.getInt("numHeaderRows");
            table.nDataRow = json.getInt("numDataRows");

            table.header = new Cell[table.nHeaderRow][table.nColumn];
            table.data = new Cell[table.nDataRow][table.nColumn];

            JSONArray headerContent = json.getJSONArray("tableHeaders");
            JSONArray dataContent = json.getJSONArray("tableData");
            for (int c = 0; c < table.nColumn; ++c) {
                for (int r = 0; r < table.nHeaderRow; ++r) {
                    table.header[r][c] = parseCellFromJSONObject(headerContent.getJSONArray(r).getJSONObject(c));
                }
                for (int r = 0; r < table.nDataRow; ++r) {
                    table.data[r][c] = parseCellFromJSONObject(dataContent.getJSONArray(r).getJSONObject(c));
                }
            }

            table.isNumericColumn = new boolean[table.nColumn];

            // TODO: need to check using another tool.
            JSONArray numericColumns = json.getJSONArray("numericColumns");
            for (int i = 0; i < numericColumns.length(); ++i) {
                table.isNumericColumn[numericColumns.getInt(i)] = true;
            }

            table.source = "WIKIPEDIA:Link:" + "https://en.wikipedia.org/wiki/" + URLEncoder.encode(json.getString("pgTitle").replaceAll("\\s", "_"));
            table.caption = json.has("tableCaption") ? json.getString("tableCaption") : null;

            // Conservative filters.
            if (table.nHeaderRow == 0) {
                return null;
            }

            return table;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
