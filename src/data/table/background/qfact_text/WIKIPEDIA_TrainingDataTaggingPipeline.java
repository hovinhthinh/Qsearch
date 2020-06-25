package data.table.background.qfact_text;

import data.text.wikipedia.WIKIPEDIA;
import data.text.wikipedia.WIKIPEDIA_EntityTaggingNode;
import model.text.Paragraph;
import pipeline.text.*;
import util.Gson;
import util.distributed.String2StringMap;

import java.util.List;
import java.util.stream.Collectors;


public class WIKIPEDIA_TrainingDataTaggingPipeline extends String2StringMap {

    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new WIKIPEDIA_EntityTaggingNode(),
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
        Paragraph paragraph = WIKIPEDIA.parseFromJSON(input);
        if (paragraph == null || !pipeline.tag(paragraph)) {
            return null;
        }
        return paragraph.sentences.stream().map(o -> Gson.toJson(o)).collect(Collectors.toList());
    }
}
