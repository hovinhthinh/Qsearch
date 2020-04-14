package eval.wiki_diff;

import com.google.gson.Gson;
import eval.TruthTable;
import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import nlp.NLP;
import nlp.YagoType;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.FileUtils;
import util.SelfMonitor;
import util.Triple;

import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;

public class WikiDiffReader {
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
            String e = StringEscapeUtils.unescapeJava(linkI.getJSONObject("target").getString("title"));
            if (!YagoType.entityExists("<" + e + ">")) {
                continue;
            }
            el.target = "WIKIPEDIA:" + linkI.getString("linkType") + ":" + e;
            el.candidates = new LinkedList<>();
            el.candidates.add(new Triple<>("<" + e + ">", -1, null));

            if (cell.entityLinks.size() == 0) {
                cell.entityLinks.add(el);
            } else {
                if (cell.entityLinks.get(0).text.split(" ").length < el.text.split(" ").length) {
                    cell.entityLinks.clear();
                    cell.entityLinks.add(el);
                }
            }
        }

        return cell;
    }

    public static Table parseFromJSON(String content) {
        try {
            JSONObject json = new JSONObject(content);
            Table table = new Table();

            table._id = json.getString("_id");
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

//            // TODO: need to check using another tool. [FIXED, using ColumnTypeTaggingNode]
//            table.isNumericColumn = new boolean[table.nColumn];
//            JSONArray numericColumns = json.getJSONArray("numericColumns");
//            for (int i = 0; i < numericColumns.length(); ++i) {
//                table.isNumericColumn[numericColumns.getInt(i)] = true;
//            }

            table.source = "WIKIPEDIA:Link:" + "https://en.wikipedia.org/wiki/" + URLEncoder.encode(json.getString("pgTitle").replaceAll("\\s", "_"));
            table.caption = json.has("tableCaption")
                    ? String.join(" ", NLP.tokenize(json.getString("tableCaption")))
                    : null;

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

    public static void main(String[] args) {
        PrintWriter out = FileUtils.getPrintWriter("eval/wiki_diff/table_ground.gz", "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream("eval/wiki_diff/table_data.gz", "UTF-8");

        Gson gson = new Gson();

        SelfMonitor m = new SelfMonitor(WikiDiffReader.class.getName(), -1, 60);
        m.start();
        for (String line : stream) {
            m.incAndGet();
            Table table = parseFromJSON(line); // already contains Entity Tags
            if (table == null) {
                continue;
            }
            TruthTable truthTable = TruthTable.fromTable(table);
            for (int r = 0; r < table.nDataRow; ++r) {
                for (int c = 0; c < table.nColumn; ++c) {
                    EntityLink el = truthTable.data[r][c].getRepresentativeEntityLink();
                    if (el != null) {
                        truthTable.bodyEntityTarget[r][c] = el.candidates.get(0).first;
                    } else {
                        truthTable.data[r][c].entityLinks.clear();
                        truthTable.data[r][c].resetCachedRepresentativeLink();
                    }
                }
            }

            out.println(gson.toJson(truthTable));
        }
        m.forceShutdown();
        out.close();
    }
}
