package data.text.wikipedia;

import com.google.gson.Gson;
import model.text.Paragraph;
import pipeline.text.*;
import util.FileUtils;

import java.io.PrintWriter;

@Deprecated
public class WIKIPEDIA_DeepTaggingPipeline_1stStage {

    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new WIKIPEDIA_EntityTaggingNode(),
                new TimeTaggingNode(true, false),
                new SentenceLengthFiltering(4, 40),
                new EntityFilteringNode(),
                new QuantityTaggingNode(),
                new QuantityFilteringNode());
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
            Paragraph paragraph = WIKIPEDIA.parseFromJSON(line);
            if (paragraph == null) {
                continue;
            }
            if (!pipeline.tag(paragraph)) {
                continue;
            }
            out.println(gson.toJson(paragraph));
        }
        out.close();
    }
}
