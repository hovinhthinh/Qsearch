package eval.table.wiki_random;

import org.json.JSONObject;
import util.FileUtils;
import util.SelfMonitor;

import java.io.PrintWriter;
import java.util.HashSet;

public class WikiRandomGenerateDataset {
    public static void main(String[] args) {
        HashSet<String> tableIdHash = new HashSet<>();
        for (String line : FileUtils.getLineStream("eval/table/wiki_random/wiki_links-random.txt", "UTF-8")) {
            tableIdHash.add(line);
        }
        SelfMonitor m = new SelfMonitor("Monitor", -1, 10);
        m.start();
        PrintWriter out = FileUtils.getPrintWriter("eval/table/wiki_random/wiki_random_data.gz", "UTF-8");
        for (String line : FileUtils.getLineStream("/GW/D5data-11/hvthinh/TabEL/TabEL+id.json.shuf.gz", "UTF-8")) {
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
