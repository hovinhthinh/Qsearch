package eval.wiki_diff;

import org.json.JSONObject;
import util.FileUtils;
import util.SelfMonitor;

import java.io.PrintWriter;
import java.util.HashSet;

public class WikiDiffGenerateDataset {
    public static void main(String[] args) {
        HashSet<String> tableIdHash = new HashSet<>();
        for (String line : FileUtils.getLineStream("eval/wiki_diff/table.id.txt", "UTF-8")) {
            tableIdHash.add(line);
        }
        SelfMonitor m = new SelfMonitor("Monitor", -1, 10);
        m.start();
        PrintWriter out = FileUtils.getPrintWriter("eval/wiki_diff/table_data.gz", "UTF-8");
        for (String line : FileUtils.getLineStream("/GW/D5data-12/hvthinh/wikipedia_dump/enwiki-20200301-pages-articles-multistream.xml.bz2.tables+id.gz", "UTF-8")) {
            m.incAndGet();
            JSONObject o = new JSONObject(line);
            String hash = o.getInt("pgId") + "\t" + o.getInt("tableId");
            if (tableIdHash.contains(hash)) {
                out.println(line);
            }
        }
        m.forceShutdown();
        out.close();
    }
}
