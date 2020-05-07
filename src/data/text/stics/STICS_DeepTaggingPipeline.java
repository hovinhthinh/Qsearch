package data.text.stics;

import com.google.gson.Gson;
import model.quantity.QuantityDomain;
import model.text.Paragraph;
import model.text.Sentence;
import pipeline.text.PostFilteringNode;
import pipeline.text.QuantityTaggingNode;
import pipeline.text.TaggingPipeline;
import pipeline.text.TimeTaggingNode;
import pipeline.text.DeepTaggingNode;
import pipeline.text.EntityFilteringNode;
import pipeline.text.QuantityFilteringNode;
import pipeline.text.SentenceLengthFiltering;
import util.FileUtils;

import java.io.PrintWriter;


public class STICS_DeepTaggingPipeline {

    public static TaggingPipeline getDefaultTaggingPipeline(String deepModelPath) {
        return new TaggingPipeline(
                new STICS_EntityTaggingNode(0.5),
                new TimeTaggingNode(),
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
//        args = "/home/hvthinh/datasets/STICS/news-en-documents_20181120.tar.gz ./temp/output
// ./deep/data/stics+nyt/length/model".split("\\s++");

        TaggingPipeline pipeline = getDefaultTaggingPipeline(args[2]);
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        Gson gson = new Gson();

        for (String line : stream) {
            Paragraph paragraph = STICS.parseFromJSON(line);
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
        out.close();
    }
}
