package data.table.tablem;

import model.table.Table;
import nlp.Static;
import pipeline.TaggingPipeline;
import util.FileUtils;
import util.Gson;
import util.SelfMonitor;
import util.distributed.String2StringMap;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class TABLEM_TaggingPipeline extends String2StringMap {
    @Override
    public void before() {
        Static.getOpenIe();
        Static.getIllinoisQuantifier();
    }

    TaggingPipeline pipeline = TaggingPipeline.getDefaultTaggingPipeline();

    // Args: <input> <output>
    public static void main(String[] args) {
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        TABLEM_TaggingPipeline p = new TABLEM_TaggingPipeline();

        SelfMonitor m = new SelfMonitor(TABLEM_TaggingPipeline.class.getName(), -1, 60);
        m.start();
        for (String line : stream) {
            m.incAndGet();
            List<String> result = p.map(line);
            if (result == null) {
                continue;
            }
            for (String r : result) {
                out.println(r);
            }
        }
        m.forceShutdown();
        out.close();
    }

    @Override
    public List<String> map(String input) {
        Table table = TABLEM.parseFromJSON(input);
        if (table == null || !pipeline.tag(table)) {
            return null;
        }
        return Arrays.asList(Gson.toJson(table));
    }
}
