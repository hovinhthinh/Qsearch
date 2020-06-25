package data.table.background.qfact_text;

import data.text.stics.STICS;
import data.text.stics.STICS_EntityTaggingNode;
import model.text.Paragraph;
import pipeline.text.*;
import util.Gson;
import util.distributed.String2StringMap;

import java.util.List;
import java.util.stream.Collectors;


public class STICS_TrainingDataTaggingPipeline extends String2StringMap {

    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new STICS_EntityTaggingNode(0),
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

    TaggingPipeline pipeline = getDefaultTaggingPipeline();

    @Override
    public List<String> map(String input) {
        Paragraph paragraph = STICS.parseFromJSON(input);
        if (paragraph == null || !pipeline.tag(paragraph)) {
            return null;
        }
        return paragraph.sentences.stream().map(o -> Gson.toJson(o)).collect(Collectors.toList());
    }

    static {
//        ARGS = "/home/hvthinh/datasets/STICS/news-en-documents_20181120.tar.gz ./tmp".split("\\s++");
    }
}
