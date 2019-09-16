package data.wikipedia;


import com.google.gson.Gson;
import model.table.Table;
import pipeline.*;
import util.FileUtils;

import java.io.PrintWriter;

public class WIKIPEDIA_DeepTaggingPipeline {
    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new EntityFilteringNode(),
                new QuantityTaggingNode(),
                new QuantityFilteringNode(),
                new DeepColumnScoringNode(),
                new PostFilteringNode()
        );
    }

    // Args: <input> <output>
    public static void main(String[] args) {
//        args = "/home/hvthinh/datasets/STICS/news-en-documents_20181120.tar.gz ./temp/output
// ./deep/data/stics+nyt/length/model".split("\\s++");

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
