package data.table.tablem;

import model.table.Table;
import nlp.Static;
import pipeline.table.QfactTaxonomyGraph;
import pipeline.table.TaggingPipeline;
import util.Gson;
import util.distributed.String2StringMap;

import java.util.Arrays;
import java.util.List;

public class TABLEM_TaggingPipeline extends String2StringMap {
    @Override
    public void before() {
        Static.getOpenIe();
        Static.getIllinoisQuantifier();
        QfactTaxonomyGraph.getDefaultGraphInstance();
    }

    TaggingPipeline pipeline = TaggingPipeline.getDefaultTaggingPipeline();

    @Override
    public List<String> map(String input) {
        Table table = TABLEM.parseFromJSON(input);
        if (table == null || !pipeline.tag(table)) {
            return null;
        }
        return Arrays.asList(Gson.toJson(table));
    }
}
