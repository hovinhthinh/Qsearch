package eval.equity;

import eval.TruthTable;
import model.table.Cell;
import model.table.link.EntityLink;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import util.FileUtils;
import util.db.Database;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

// read Equity and convert to TruthTable format.
public class EquityReader {
    public static HashMap<String, String> getContentMap() {
        Database.openNewConnection("yibrahim");
        ArrayList<ArrayList<String>> content = Database.select("SELECT * FROM evaluation.table_data");

        HashMap<String, String> map = new HashMap<>();
        for (ArrayList<String> a : content) {
            map.put(a.get(3), a.get(4));
        }
        return map;
    }

    public static void main(String[] args) {
        PrintWriter out = FileUtils.getPrintWriter("eval/equity/dataset/AnnotatedTables-19092016/dataset_ground.json", "UTF-8");

        JSONObject o = new JSONObject(FileUtils.getContent("eval/equity/dataset/AnnotatedTables-19092016/dataset.json", "UTF-8"));

        HashMap<String, String> contentMap = getContentMap();
        for (String k : o.keySet()) {
            JSONObject ts = o.getJSONObject(k);
            JSONArray tables = ts.getJSONArray("tables");
            for (int i = 0; i < tables.length(); ++i) {
                TruthTable table = new TruthTable();
                table.surroundingText = ts.getString("content");
                table.source = ts.getString("url");
                table.pageTitle = ts.getString("title");
                table._id = "EQUITY:" + i + ":" + ts.getString("id");

                JSONObject t = tables.getJSONObject(i);

                table.nColumn = t.getInt("ncol");
                table.nHeaderRow = 1;
                table.nDataRow = t.getInt("nrow") - 1;

                String[] rows = contentMap.get(ts.getString("id")).split("\n");
                Assert.assertTrue(rows.length == table.nDataRow + 1);

                String[][] content = new String[rows.length][];
                for (int j = 0; j < content.length; ++j) {
                    content[j] = rows[j].split("\t", -1);
                    Assert.assertTrue(content[j].length >= table.nColumn);
                }

                table.header = new Cell[table.nHeaderRow][table.nColumn];
                table.data = new Cell[table.nDataRow][table.nColumn];

                for (int c = 0; c < table.nColumn; ++c) {
                    table.header[0][c] = new Cell();
                    table.header[0][c].text = content[0].length > c ? String.join(" ", NLP.tokenize(content[0][c])) : "";
                    for (int r = 0; r < table.nDataRow; ++r) {
                        table.data[r][c] = new Cell();
                        table.data[r][c].text = content[r + 1].length > c ? String.join(" ", NLP.tokenize(content[r + 1][c])) : "";
                    }
                }

                JSONArray annotations = t.getJSONArray("annotations");
                for (int j = 0; j < annotations.length(); ++j) {
                    JSONObject a = annotations.getJSONObject(j);
                    String type = a.getString("type");
                    if (!(type.equals("ENTITY") || type.equals("LOCATION") || type.equals("ORGANIZATION") || type.equals("PERSON"))) {
                        continue;
                    }
                    int row = a.getInt("row");
                    int col = a.getInt("col");
                    String span = content[row][col].substring(a.getInt("start"), a.getInt("end"));
                    String target = a.getString("sem_target");

                    Cell cell = row == 0 ? table.header[row][col] : table.data[row - 1][col];
                    if (cell.entityLinks == null) {
                        cell.entityLinks = new ArrayList<>();
                    }
                    EntityLink link = new EntityLink();
                    link.target = target;
                    link.text = String.join(" ", NLP.tokenize(span));

                    Assert.assertTrue(cell.text.contains(link.text));

                    cell.entityLinks.add(link);
                }

                out.println(table.toString());
            }
        }

        out.close();
    }
}
