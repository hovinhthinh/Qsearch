package data.text.stics;

import model.text.Paragraph;
import pipeline.text.*;
import util.FileUtils;
import util.Gson;

import java.io.PrintWriter;

@Deprecated
public class STICS_DeepTaggingPipeline_1stStage {

    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new STICS_EntityTaggingNode(0.5),
                new TimeTaggingNode(),
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

        for (String line : stream) {
            Paragraph paragraph = STICS.parseFromJSON(line);
            if (paragraph == null) {
                continue;
            }
            if (!pipeline.tag(paragraph)) {
                continue;
            }
            out.println(Gson.toJson(paragraph));
        }
        out.close();
    }
}
