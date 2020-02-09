package eval.equity;

import com.google.gson.Gson;
import eval.TruthTable;
import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import nlp.NLP;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import util.FileUtils;
import util.Triple;
import util.db.Database;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

// read Equity and convert to TruthTable format.
public class EquityReader {
    public static HashMap<String, String> getContentMap() {
        ArrayList<ArrayList<String>> content = Database.select("SELECT * FROM evaluation.table_data");

        HashMap<String, String> map = new HashMap<>();
        for (ArrayList<String> a : content) {
            map.put(a.get(3), a.get(4));
        }
        return map;
    }

    public static void main(String[] args) {
        Database.openNewConnection("yibrahim");

        // Yusra annotations!
        ArrayList<ArrayList<String>> ground = Database.select("select * from evaluation.table_evaluation inner join evaluation.table_annotations \n" +
                "on evaluation.table_evaluation.document_id = evaluation.table_annotations.document_id \n" +
                "and evaluation.table_evaluation.annotation_id = evaluation.table_annotations.annotation_id \n");
        HashMap<String, String> yusraValue = new HashMap<>();
        for (ArrayList<String> r : ground) {
            String key = r.get(1) + "@" + r.get(9) + "@" + r.get(10) + "@" + r.get(11) + "@" + r.get(12);
            if (yusraValue.containsKey(key)) {
                System.out.println("Duplicated key: " + key);
            }
            yusraValue.put(r.get(1) + "@" + r.get(9) + "@" + r.get(10) + "@" + r.get(11) + "@" + r.get(12), r.get(4));
        }

        PrintWriter out = FileUtils.getPrintWriter("eval/equity/dataset/AnnotatedTables-19092016/dataset_ground.json", "UTF-8");
        Gson gson = new Gson();

        JSONObject o = new JSONObject(FileUtils.getContent("eval/equity/dataset/AnnotatedTables-19092016/dataset_original_yibrahim.json", "UTF-8"));

        HashMap<String, String> contentMap = getContentMap();


        for (String k : o.keySet()) {
            JSONObject ts = o.getJSONObject(k);
            JSONArray tables = ts.getJSONArray("tables");
            for (int i = 0; i < tables.length(); ++i) {
                Table table = new Table();
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
                int[][] yusraBit = new int[table.nDataRow][table.nColumn];

                for (int c = 0; c < table.nColumn; ++c) {
                    table.header[0][c] = new Cell();
                    table.header[0][c].entityLinks = new ArrayList<>();
                    table.header[0][c].text = content[0].length > c ? String.join(" ", NLP.tokenize(content[0][c])) : "";
                    for (int r = 0; r < table.nDataRow; ++r) {
                        table.data[r][c] = new Cell();
                        table.data[r][c].entityLinks = new ArrayList<>();
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

                    String key = ts.getString("id") + "@" + row + "@" + col + "@" + a.getInt("start") + "@" + a.getInt("end");

                    String target = a.getString("sem_target");
                    if (target.equals("Monetary(Currency,Euro)")) {
                        continue;
                    }

                    Cell cell = row == 0 ? table.header[row][col] : table.data[row - 1][col];

                    if (row > 0) {
                        try {
                            yusraBit[row - 1][col] = Integer.parseInt(yusraValue.get(key));
                        } catch (Exception e) {
                            System.out.println("--- Key not found: ---");
                            System.out.println("Key " + key);
                            System.out.println("Span: " + span);
                            System.out.println("Target: " + target);
                            System.out.println("==================================================");
                        }
                    }

                    EntityLink link = new EntityLink();
                    link.target = "YAGO:" + target;
                    link.candidates = new LinkedList<>();
                    link.candidates.add(new Triple<>("<" + target + ">", -1, null));

                    link.text = String.join(" ", NLP.tokenize(span));

                    // fix
                    if (span.equals("AT&")) {
                        link.text = "AT&T";
                    }
                    Assert.assertTrue(cell.text.contains(link.text));
                    int begin = cell.text.indexOf(link.text);
                    int end = begin + link.text.length();
                    while (end < cell.text.length() && cell.text.indexOf(end) != ' ') {
                        ++end;
                    }
                    link.text = cell.text.substring(begin, end);

                    if (cell.entityLinks.size() == 0) {
                        cell.entityLinks.add(link);
                    } else {
                        if (cell.entityLinks.get(0).text.split(" ").length < link.text.split(" ").length) {
                            cell.entityLinks.clear();
                            cell.entityLinks.add(link);
                        }
                    }
                }

                TruthTable truthTable = TruthTable.fromTable(table);
                for (int r = 0; r < table.nDataRow; ++r) {
                    for (int c = 0; c < table.nColumn; ++c) {
                        EntityLink el = truthTable.data[r][c].getRepresentativeEntityLink();
                        if (el != null) {
                            truthTable.bodyEntityTarget[r][c] = el.candidates.get(0).first;
                            truthTable.yusraBodyEntityTarget[r][c] = yusraBit[r][c];
                        } else {
                            truthTable.data[r][c].entityLinks.clear();
                            truthTable.data[r][c].resetCachedRepresentativeLink();
                        }
                    }
                }
                out.println(gson.toJson(truthTable));
            }
        }

        out.close();
    }
}
