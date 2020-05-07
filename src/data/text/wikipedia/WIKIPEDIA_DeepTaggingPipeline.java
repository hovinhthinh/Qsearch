package data.text.wikipedia;

import com.google.gson.Gson;
import model.quantity.QuantityDomain;
import model.text.Paragraph;
import model.text.Sentence;
import pipeline.text.*;
import util.FileUtils;

import java.io.PrintWriter;


public class WIKIPEDIA_DeepTaggingPipeline {

    public static TaggingPipeline getDefaultTaggingPipeline(String deepModelPath) {
        return new TaggingPipeline(
                new WIKIPEDIA_EntityTaggingNode(),
                new TimeTaggingNode(true, false),
                new SentenceLengthFiltering(4, 40),
                new EntityFilteringNode(),
                new QuantityTaggingNode(),
                new QuantityFilteringNode(),
                new DeepTaggingNode(deepModelPath, QuantityDomain.Domain.ANY, Double.NEGATIVE_INFINITY),
                new PostFilteringNode()
        );
    }

    // Args: <input> <output> <deep_model_path>
    public static void main(String[] args) {
//        args = "/home/hvthinh/datasets/WIKI/fixedWikipediaEntitiesJSON.gz /home/hvthinh/datasets/WIKI/fixedWikipediaEntitiesJSON_full.gz deep/data/stics+nyt/all/model/".split(" ");

        TaggingPipeline pipeline = getDefaultTaggingPipeline(args[2]);
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
            for (Sentence sent : paragraph.sentences) {
                out.println(gson.toJson(sent));
            }
        }
    }
}
