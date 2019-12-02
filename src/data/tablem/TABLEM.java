package data.tablem;

import model.table.Cell;
import model.table.Table;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TABLEM {
    // Input is from "/GW/D5data-10/hvthinh/BriQ-TableM/*.gz"
    private static Cell parseCellFromJSONObject(String surfaceText) {
        Cell cell = new Cell();
        cell.text = String.join(" ", NLP.tokenize(surfaceText));

        return cell;
    }

    public static Table parseFromJSON(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            JSONArray tableData = json.getJSONArray("relation");

            Table table = new Table();
            table._id = json.getString("_id");

            table.nColumn = tableData.length();
            if (table.nColumn <= 1) {
                return null;
            }
            int nRows = tableData.getJSONArray(0).length();
            if (nRows <= 1) {
                return null;
            }
            table.nHeaderRow = 1;
            table.nDataRow = nRows - 1;

            table.header = new Cell[table.nHeaderRow][table.nColumn];
            table.data = new Cell[table.nDataRow][table.nColumn];

            for (int c = 0; c < table.nColumn; ++c) {
                JSONArray columnData = tableData.getJSONArray(c);
                table.header[0][c] = parseCellFromJSONObject(columnData.getString(0));
                for (int r = 0; r < table.nDataRow; ++r) {
                    table.data[r][c] = parseCellFromJSONObject(columnData.getString(r + 1));
                }
            }

            table.source = "TABLEM:Link:" + json.getString("url");
            table.pageTitle = json.has("pageTitle") ? json.getString("pageTitle") : null;
            table.caption = json.has("title") ? json.getString("title") : null;

            return table;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
