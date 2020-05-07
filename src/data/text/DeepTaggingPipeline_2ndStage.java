package data.text;

import com.google.gson.Gson;
import model.text.Paragraph;
import model.text.Sentence;
import pipeline.text.DeepTaggingNode;
import pipeline.text.PostFilteringNode;
import pipeline.text.TaggingPipeline;
import util.FileUtils;

import java.io.PrintWriter;

@Deprecated
public class DeepTaggingPipeline_2ndStage {

    public static TaggingPipeline getDefaultTaggingPipeline(String deepModelPath, String domain) {
        return new TaggingPipeline(
                new DeepTaggingNode(deepModelPath, domain, Double.NEGATIVE_INFINITY),
                new PostFilteringNode());
    }

    // Args: <input> <output> <deep_model_path> [domain]
    public static void main(String[] args) {
//        args = "./data/stics+nyt/full_1st.gz.part72.gz ./data/stics+nyt/full.gz.part72.gz
// ./data/stics+nyt/all/model".split("\\s++");

        String domain = "ANY";
        if (args.length > 3) {
            domain = args[3];
        }
        TaggingPipeline pipeline = getDefaultTaggingPipeline(args[2], domain);
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        Gson gson = new Gson();

        for (String line : stream) {
            Paragraph paragraph = gson.fromJson(line, Paragraph.class);
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
