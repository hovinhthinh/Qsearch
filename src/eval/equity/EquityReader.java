package eval.equity;

import eval.TruthTable;
import model.table.Cell;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONObject;
import util.FileUtils;

import java.io.PrintWriter;

// read Equity and convert to TruthTable format.
public class EquityReader {
    public static void main(String[] args) {
        PrintWriter out = FileUtils.getPrintWriter("eval/equity/dataset/AnnotatedTables-19092016/dataset_ground.json", "UTF-8");

        JSONObject o = new JSONObject(FileUtils.getContent("eval/equity/dataset/AnnotatedTables-19092016/dataset.json", "UTF-8"));
        int n = 0;
        int nErr = 0;
        int nTotal = 0;
        for (String k : o.keySet()) {
            JSONObject ts = o.getJSONObject(k);
            System.out.println(ts.toString());
            JSONArray tables = ts.getJSONArray("tables");
            for (int i = 0; i < tables.length(); ++i) {
                TruthTable table = new TruthTable();
                table.surroundingText = ts.getString("content");
                table.source = ts.getString("url");
                table.pageTitle = ts.getString("title");
                table._id = "EQUITY:" + i + ":" + ts.getString("id");

                JSONObject t = tables.getJSONObject(i);


                String[] rows = t.getString("content").split("\n");
                String[][] content = new String[rows.length][];
                for (int j = 0; j < content.length; ++j) {
                    content[j] = rows[j].split("\t");
                    table.nColumn = Math.max(table.nColumn, content[j].length);
                }

                table.nHeaderRow = 1;
                table.nDataRow = rows.length - 1;


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
                    int row = a.getInt("row") - 1;
                    int col = a.getInt("col");

                    if (row < 0) {
                        continue;
                    }

                    if (row >= table.nDataRow) {
                        ++nErr;
//                        System.out.println("ERROR");
//                        System.out.println(t.getString("content"));
//                        System.out.println(a.toString());
//                        System.exit(1);
                    } // total 199 errs
                    else {
                        ++nTotal;
                    }
                    // TODO
                }

                out.println(table.toString());
            }
        }

        out.close();
        System.out.println(nErr + "/" + nTotal);
    }
}
