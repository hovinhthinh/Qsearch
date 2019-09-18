package data.wikipedia;


import com.google.gson.Gson;
import model.table.Table;
import pipeline.DeepColumnScoringNode;
import pipeline.PostFilteringNode;
import pipeline.QuantityTaggingNode;
import pipeline.TaggingPipeline;
import util.FileUtils;

import java.io.PrintWriter;

public class WIKIPEDIA_DeepTaggingPipeline {
    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new QuantityTaggingNode(),
                new DeepColumnScoringNode(0),
                new PostFilteringNode()
        );
    }

    // Args: <input> <output>
    public static void main(String[] args) {
//        args = "/local/home/hvthinh/datasets/TabEL.json.shuf.gz /local/home/hvthinh/datasets/TabEL.json.shuf.out.gz".split("\\s++");

        TaggingPipeline pipeline = getDefaultTaggingPipeline();
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        Gson gson = new Gson();

        for (String line : stream) {
            Table table = WIKIPEDIA.parseFromJSON(line);
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
