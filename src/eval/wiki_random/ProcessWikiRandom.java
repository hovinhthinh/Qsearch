package eval.wiki_random;

import com.google.gson.Gson;
import data.wikipedia.WIKIPEDIA;
import model.table.Table;
import pipeline.*;
import util.FileUtils;
import util.SelfMonitor;

import java.io.PrintWriter;

public class ProcessWikiRandom {
    // Just the annotations of entities and quantities, there is no linking.
    public static TaggingPipeline getAnnotationPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                new TimeTaggingNode(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode(0.3, 0.3)
        );
    }

    public static void main(String[] args) {
        TaggingPipeline pipeline = getAnnotationPipeline();
        PrintWriter out = FileUtils.getPrintWriter("eval/wiki_random/wiki_random_annotation.gz", "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream("eval/wiki_random/wiki_random_data.gz", "UTF-8");

        Gson gson = new Gson();

        SelfMonitor m = new SelfMonitor(ProcessWikiRandom.class.getName(), -1, 60);
        m.start();
        for (String line : stream) {
            m.incAndGet();
            Table table = WIKIPEDIA.parseFromJSON(line); // already contains Entity Tags
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
