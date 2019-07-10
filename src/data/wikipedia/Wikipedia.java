package data.wikipedia;

import model.table.Cell;
import model.table.Link;
import model.table.Table;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Wikipedia {

    // Input is from "/GW/D5data/hvthinh/TabEL/tables.json.gz"
    private static Cell parseCellFromJSONObject(JSONObject json) {
        Cell cell = new Cell();
        cell.text = json.getString("text");
        JSONArray links = json.getJSONArray("surfaceLinks");

        cell.links = new Link[links.length()];
        for (int i = 0; i < links.length(); ++i) {
            cell.links[i] = new Link();
            JSONObject linkI = links.getJSONObject(i);

            cell.links[i].text = linkI.getString("surface");
            cell.links[i].target = "WIKIPEDIA:" + linkI.getString("linkType") + ":" + linkI.getJSONObject("target").getString("title");
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

            table.source = "WIKIPEDIA:" + json.getString("pgTitle");

            return table;
        } catch (JSONException e) {
            return null;
        }
    }
}
