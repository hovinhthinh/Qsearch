package data.tablem;

import com.google.gson.Gson;
import model.table.Table;
import pipeline.*;
import util.FileUtils;
import util.SelfMonitor;

import java.io.PrintWriter;

public class TABLEM_TaggingPipeline {

    // Args: <input> <output>
    public static void main(String[] args) {
//        args = "/GW/D5data-10/hvthinh/BriQ-TableM/health_combined.gz /GW/D5data-11/hvthinh/TABLEM/health_combined.out.gz".split("\\s++");

        TaggingPipeline pipeline = TaggingPipeline.getAnnotationPipeline();
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        Gson gson = new Gson();

        SelfMonitor m = new SelfMonitor(TABLEM_TaggingPipeline.class.getName(), -1, 60);
        m.start();
        for (String line : stream) {
            m.incAndGet();
            Table table = TABLEM.parseFromJSON(line);
            if (table == null) {
                continue;
            }
            if (!pipeline.tag(table)) {
                continue;
            }
            out.println(gson.toJson(table));
        }
        m.forceShutdown();
        out.close();
    }
}
