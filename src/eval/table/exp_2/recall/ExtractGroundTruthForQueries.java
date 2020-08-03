package eval.table.exp_2.recall;

import model.query.SimpleQueryParser;
import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import model.table.link.QuantityLink;
import nlp.NLP;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pipeline.table.QuantityTaggingNode;
import util.FileUtils;
import util.Gson;
import util.Triple;

import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Scanner;

class WIKIPEDIA_GroundTruth {

    // Input is from "/GW/D5data/hvthinh/TabEL/tables.json.gz"
    private static Cell parseCellFromJSONObject(JSONObject json) {
        Cell cell = new Cell();
        cell.text = String.join(" ", NLP.tokenize(json.getString("text")));
        JSONArray links = json.getJSONArray("surfaceLinks");
        cell.entityLinks = new ArrayList<>();
        cell.timeLinks = new ArrayList<>();
        cell.quantityLinks = new ArrayList<>();

        for (int i = 0; i < links.length(); ++i) {
            JSONObject linkI = links.getJSONObject(i);
            EntityLink el = new EntityLink();
            el.text = String.join(" ", NLP.tokenize(linkI.getString("surface")));
            el.target = "<" + StringEscapeUtils.unescapeJava(linkI.getJSONObject("target").getString("title")) + ">";
            cell.entityLinks.add(el);
        }
        return cell;
    }

    public static Table parseFromJSON(JSONObject json) {
        try {
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

class GroundFact {
    String surface;
    String quantityStr;
    String entity;
    String quantityValue;

    public GroundFact(String surface, String quantityStr, String entity, String quantityValue) {
        this.surface = surface;
        this.quantityStr = quantityStr;
        this.entity = entity;
        this.quantityValue = quantityValue;
    }

    @Override
    public String toString() {
        return surface + "\t" + quantityStr + "\t" + entity + "\t" + quantityValue;
    }
}

class GroundTable {
    String quantityHeader;
    ArrayList<GroundFact> facts;

    public GroundTable() {
        facts = new ArrayList<>();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("----------------------------------------------------------------" + "\t" + quantityHeader);
        for (GroundFact f : facts) {
            sb.append("\n").append(f.toString());
        }
        return sb.toString();
    }
}

class RecallQuery {
    String full;
    String type, context, quantity;
    boolean isUpperBoundQuery;

    String groundTruthURLFull;
    String groundTruthURL;
    String pgTitle;
    String sectionTitle;
    int eCol, qCol;

    String note;

    ArrayList<GroundTable> tables;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(">>>> BEGIN >>>>");
        sb.append("\n").append(full + "\t" + type + "\t" + context + "\t" + quantity + "\t" + (isUpperBoundQuery ? "UB" : " LB"));
        sb.append("\n").append(groundTruthURLFull);
        for (GroundTable t : tables) {
            sb.append("\n").append(t.toString());
        }
        sb.append("\n").append("<<<< END <<<<");
        return sb.toString();
    }
}

public class ExtractGroundTruthForQueries {

    public static void filterRelevantTables(ArrayList<RecallQuery> queries) {
        PrintWriter out = FileUtils.getPrintWriter("eval/table/exp_2/recall/relevant_tables", "UTF-8");
        for (String line : FileUtils.getLineStream("/home/hvthinh/datasets/wikipedia_dump/enwiki-20200301-pages-articles-multistream.xml.bz2.tables+id.gz", "UTF-8")) {
            JSONObject o = new JSONObject(line);
            String title = o.getString("pgTitle");
            String[] subtitles = o.getString("sectionTitles").split("\r\n");

            for (RecallQuery q : queries) {
                if (!q.pgTitle.equals(title)) {
                    continue;
                }
                if (q.sectionTitle != null) {
                    boolean goodTable = false;
                    for (String st : subtitles) {
                        if (st.equals(q.sectionTitle)) {
                            goodTable = true;
                            break;
                        }
                    }
                    if (!goodTable) {
                        continue;
                    }
                }
                out.println(line);
                break;
            }
        }
        out.close();
    }

    public static void main(String[] args) throws Exception {

        QuantityTaggingNode qtn = new QuantityTaggingNode();
        Scanner in = new Scanner(System.in);
        // LOAD QUERIES
        ArrayList<RecallQuery> queries = new ArrayList<>();
        boolean header = true;
        for (String line : FileUtils.getLineStream("eval/table/exp_2/recall/queries_raw.txt", "UTF-8")) {
            if (header) {
                header = false;
                continue;
            }
            String[] arr = line.split("\t", -1);
            RecallQuery query = new RecallQuery();

            query.full = arr[0];
            Triple<String, String, String> parsed = SimpleQueryParser.parse(query.full);
            query.type = parsed.first;
            query.context = parsed.second;
            query.quantity = parsed.third;
            query.isUpperBoundQuery = arr.length > 4 && arr[4].trim().equals("1");

            query.groundTruthURL = query.groundTruthURLFull = arr[1];
            int refPos = query.groundTruthURL.indexOf("#");
            if (refPos != -1) {
                query.sectionTitle = URLDecoder.decode(query.groundTruthURL.substring(refPos + 1), "UTF-8").replaceAll("_", " ");
                query.groundTruthURL = query.groundTruthURL.substring(0, refPos);
            }
            query.pgTitle = URLDecoder.decode(query.groundTruthURL.substring(query.groundTruthURL.lastIndexOf("/") + 1), "UTF-8").replaceAll("_", " ");
            query.eCol = Integer.parseInt(arr[2]);
            query.qCol = Integer.parseInt(arr[3]);
            query.tables = new ArrayList<>();
            query.note = arr.length > 5 ? arr[5].trim() : "";
            System.out.println(Gson.toJson(query));
            queries.add(query);
        }
        filterRelevantTables(queries);
        // PROCESS
        PrintWriter out = FileUtils.getPrintWriter("eval/table/exp_2/recall/queries_groundtruth_template.txt", "UTF-8");
        ArrayList<String> tables = FileUtils.getLines("eval/table/exp_2/recall/relevant_tables", "UTF-8");
        for (RecallQuery q : queries) {
            for (String line : tables) {
                JSONObject o = new JSONObject(line);
                String title = o.getString("pgTitle");
                String[] subtitles = o.getString("sectionTitles").split("\r\n");
                if (!q.pgTitle.equals(title)) {
                    continue;
                }
                if (q.sectionTitle != null) {
                    boolean goodTable = false;
                    for (String st : subtitles) {
                        if (st.equals(q.sectionTitle)) {
                            goodTable = true;
                            break;
                        }
                    }
                    if (!goodTable) {
                        continue;
                    }
                }

                Table t = WIKIPEDIA_GroundTruth.parseFromJSON(o);
                if (t == null) {
                    continue;
                }
                qtn.process(t);
                if (t.nColumn <= Math.max(q.eCol, q.qCol)) {
                    continue;
                }
                // NOW DO
                System.out.println("================================================================");
                System.out.println(q.full);
                System.out.println(q.groundTruthURLFull);
                for (String st : subtitles) {
                    System.out.print(st + "  -->  ");
                }
                System.out.println();
                System.out.println(t.getTableContentPrintable(false, true, false));
                GroundTable gt = new GroundTable();
                gt.quantityHeader = t.getOriginalCombinedHeader(q.qCol);
                for (int i = 0; i < t.nDataRow; ++i) {
                    EntityLink el = t.data[i][q.eCol].getRepresentativeEntityLink();
                    QuantityLink ql = t.data[i][q.qCol].getRepresentativeQuantityLink();

                    GroundFact f = new GroundFact(
                            t.data[i][q.eCol].text,
                            t.data[i][q.qCol].text,
                            el == null ? "" : el.target,
                            ql == null ? "" : ql.quantity.toString());
                    gt.facts.add(f);
                }
                for (GroundFact f : gt.facts) {
                    System.out.println(String.format("[%30s]    [%30s]    [%30s]    [%30s]",
                            f.surface, f.quantityStr, f.entity, f.quantityValue));
                }
                q.tables.add(gt);
//                if (q.note.isEmpty()) {
//                    q.tables.add(gt);
//                    continue;
//                }
//                do {
//                    System.out.print("Option_(y/n)$ ");
//                    System.out.flush();
//                    String opt = in.nextLine().trim().toLowerCase();
//                    if (opt.equals("y")) {
//                        q.tables.add(gt);
//                        break;
//                    } else if (opt.equals("n")) {
//                        break;
//                    } else {
//                        System.out.println("Invalid");
//                    }
//                } while (true);
            }
            out.println(q.toString());
            out.println();
            out.flush();
        }
        out.close();
    }
}
