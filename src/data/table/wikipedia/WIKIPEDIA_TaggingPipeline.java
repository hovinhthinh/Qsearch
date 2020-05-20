package data.table.wikipedia;

import model.table.Table;
import nlp.Static;
import pipeline.table.*;
import util.FileUtils;
import util.Gson;
import util.SelfMonitor;
import util.distributed.String2StringMap;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class WIKIPEDIA_TaggingPipeline extends String2StringMap {
    @Override
    public void before() {
        Static.getOpenIe();
        Static.getIllinoisQuantifier();
        QfactTaxonomyGraph.getDefaultGraphInstance();
    }

    TaggingPipeline pipeline = getDefaultTaggingPipeline();

    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                TimeTaggingNode.getDefaultInstance(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode(),
                TextBasedColumnScoringNode.getDefaultInferenceInstance(),
                new ColumnLinkFilteringNode(0),
                new PostFilteringNode()
        );
    }

    // Just the annotations of entities and quantities, there is no linking.
    public static TaggingPipeline getAnnotationPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                TimeTaggingNode.getDefaultInstance(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode()
        );
    }

    // Args: <input> <output>
    public static void main(String[] args) {
//        args = "/local/home/hvthinh/datasets/TabEL.json.shuf.gz /local/home/hvthinh/datasets/TabEL.json.shuf.out.gz".split("\\s++");

        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        WIKIPEDIA_TaggingPipeline p = new WIKIPEDIA_TaggingPipeline();

        SelfMonitor m = new SelfMonitor(WIKIPEDIA_TaggingPipeline.class.getName(), -1, 60);
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
        Table table = WIKIPEDIA.parseFromJSON(input); // already contains Entity Tags
        if (table == null || !pipeline.tag(table)) {
            return null;
        }
        return Arrays.asList(Gson.toJson(table));
    }
}
