package data.wikipedia;


import com.google.gson.Gson;
import model.table.Table;
import pipeline.*;
import util.FileUtils;

import java.io.PrintWriter;

public class WIKIPEDIA_DeepTaggingPipeline {

    @Deprecated
    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                new TimeTaggingNode(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode(),
                new DeepColumnScoringNode(DeepColumnScoringNode.JOINT_INFERENCE),
                new ColumnLinkFilteringNode(0),
                new PostFilteringNode()
        );
    }

    // Just the annotations of entities and quantities, there is no linking.
    public static TaggingPipeline getAnnotationPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                new TimeTaggingNode(),
                new QuantityTaggingNode()
        );
    }

    // Args: <input> <output>
    public static void main(String[] args) {
//        args = "/local/home/hvthinh/datasets/TabEL.json.shuf.gz /local/home/hvthinh/datasets/TabEL.json.shuf.out.gz".split("\\s++");

        TaggingPipeline pipeline = getAnnotationPipeline();
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        Gson gson = new Gson();

        for (String line : stream) {
            Table table = WIKIPEDIA.parseFromJSON(line); // already contains Entity Tags
            if (table == null) {
                continue;
            }
            if (!pipeline.tag(table)) {
                continue;
            }
            out.println(gson.toJson(table));
        }
        out.close();
    }
}
