package data.table.wikipedia;

import model.table.Table;
import nlp.Static;
import pipeline.table.*;
import util.Gson;
import util.distributed.String2StringMap;

import java.util.Arrays;
import java.util.List;

public class WIKIPEDIA_TaggingPipeline extends String2StringMap {
    @Override
    public void before() {
        Static.getOpenIe();
        Static.getIllinoisQuantifier();
        QfactTaxonomyGraph.getDefaultGraphInstance();
    }

    TaggingPipeline pipeline = getDefaultTaggingPipeline();

    public static TaggingPipeline getDefaultTaggingPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                TimeTaggingNode.getDefaultInstance(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode(),
                TextBasedColumnScoringNode.getDefaultInferenceInstance(),
                new ColumnLinkFilteringNode(0),
                new PostFilteringNode()
        );
    }

    // Just the annotations of entities and quantities, there is no linking.
    public static TaggingPipeline getAnnotationPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                TimeTaggingNode.getDefaultInstance(),
                new QuantityTaggingNode(),
                new ColumnTypeTaggingNode()
        );
    }

    @Override
    public List<String> map(String input) {
        Table table = WIKIPEDIA.parseFromJSON(input); // already contains Entity Tags
        if (table == null || !pipeline.tag(table)) {
            return null;
        }
        return Arrays.asList(Gson.toJson(table));
    }

    static {
//        ARGS = "/local/home/hvthinh/datasets/TabEL.json.shuf.gz /local/home/hvthinh/datasets/TabEL.json.shuf.out.gz".split("\\s++");
    }
}
