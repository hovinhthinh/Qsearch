package eval.table.wikidata;

import org.json.JSONObject;
import util.FileUtils;
import util.SelfMonitor;

import java.io.PrintWriter;

public class ExtractNumericRelations {
    public static void main(String[] args) {
        args = "/GW/D5data-13/hvthinh/wikidata/wikidata-20200629-all.json.gz ./eval/table/wikidata/numeric_predicates".split(" ");
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        SelfMonitor m = new SelfMonitor(ExtractNumericRelations.class.getName(), -1, 60);
        m.start();
        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            if (line.charAt(line.length() - 1) == ',') {
                line = line.substring(0, line.length() - 1);
            }
            try {
                JSONObject o = new JSONObject(line);
                if (o.getString("type").equals("property") && o.getString("datatype").equals("quantity")) {
                    out.println(o.getString("id"));
                }
            } catch (Exception e) {
                System.err.println("err: " + line);
            }
            m.incAndGet();
        }
        m.forceShutdown();
        out.close();
    }
}
