package data.manual.t2d;

import com.google.gson.Gson;
import data.manual.TruthTable;
import model.table.Cell;
import model.table.Table;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.FileUtils;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

// read T2D and convert to TruthTable format.
public class T2DReader {
    private static Cell parseCellFromJSONObject(String surfaceText) {
        Cell cell = new Cell();
        cell.text = String.join(" ", NLP.tokenize(surfaceText));
        return cell;
    }

    public static TruthTable parseFromJSON(String line) {
        try {
            JSONObject json = new JSONObject(line);

            TruthTable table = new TruthTable();
            table.moreInfo.put("headerPosition", json.getString("headerPosition"));
            table.moreInfo.put("tableType", json.getString("tableType"));
            table.keyColumnGroundTruth = json.getInt("keyColumnIndex");

            JSONArray tableData = json.getJSONArray("relation");
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

            table.source = "T2D:Link:" + json.getString("url");
            table.pageTitle = json.has("pageTitle") ? json.getString("pageTitle") : null;
            table.caption = json.has("title") ? json.getString("title") : null;

            return table;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        File folder = new File("./eval/T2D/extended_instance_goldstandard/tables");

        PrintWriter out = FileUtils.getPrintWriter("./eval/T2D/ground_truth", "UTF-8");

        Gson gson = new Gson();
        for (File f : folder.listFiles()) {
            if (f.isDirectory()) {
                continue;
            }
            for (String line : FileUtils.getLineStream(f, StandardCharsets.UTF_8)) {
                Table t = parseFromJSON(line);
                if (t == null) {
                    continue;
                }
                out.println(gson.toJson(t));
            }
        }

        out.close();
    }
}
