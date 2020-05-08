package data.table.background.qfact_text;

import com.google.gson.Gson;
import data.text.nyt.NYT;
import data.text.nyt.NYT_EntityTaggingNode;
import model.text.Paragraph;
import model.text.Sentence;
import pipeline.text.*;
import util.FileUtils;

import java.io.IOException;
import java.io.PrintWriter;


public class NYT_TrainingDataTaggingPipeline {

    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new NYT_EntityTaggingNode(0),
                new CoreferenceTaggingNode(),
                new SUTimeTaggingNode(),
                new SentenceLengthFiltering(4, 40),
                new EntityFilteringNode(),
                new QuantityTaggingNode(),
                new QuantityFilteringNode(),
                new POSTaggingNode(),
                new OpenIETaggingNodeTabQs(10, 0),
                new PostFilteringNode()
        );
    }

    // Args: <input> <output>
    public static void main(String[] args) throws IOException {
//        args = "/home/hvthinh/datasets/NYT/nytimes_aida.tar.bz2 ./tmp".split("\\s++");
        TaggingPipeline pipeline = getDefaultTaggingPipeline();
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");

        Gson gson = new Gson();

        for (String line : stream) {
            Paragraph paragraph = NYT.parseFromJSON(line);
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
