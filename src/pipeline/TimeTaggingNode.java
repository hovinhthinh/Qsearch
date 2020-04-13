package pipeline;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.time.SUTime;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.time.TimeExpression;
import edu.stanford.nlp.util.CoreMap;
import model.table.Table;
import model.table.link.TimeLink;

import java.util.ArrayList;
import java.util.Properties;

// Use SUTime tagger (Stanford NLP)
public class TimeTaggingNode implements TaggingNode {
    private AnnotationPipeline pipeline;

    public TimeTaggingNode() {
        Properties props = new Properties();
        pipeline = new AnnotationPipeline();
        pipeline.addAnnotator(new TokenizerAnnotator(false));
        pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        pipeline.addAnnotator(new POSTaggerAnnotator(false));
        pipeline.addAnnotator(new TimeAnnotator("sutime", props));
    }


    @Override
    public boolean process(Table table) {
        for (int c = 0; c < table.nColumn; ++c) {
            for (int r = 0; r < table.nHeaderRow; ++r) {
                // No time tagging for header cells
                // tagCell(table.header[r][c]);
                table.header[r][c].timeLinks = new ArrayList<>();
            }
            for (int r = 0; r < table.nDataRow; ++r) {
                if (table.data[r][c].entityLinks != null && table.data[r][c].entityLinks.size() > 0) {
                    // not tagging cells with entities;
                    // only in case of wikipedia, time tagging is run after entities tagging.
                    //
                    // for webtables, time tagging nodes should be done before entities tagging node.
                    table.data[r][c].timeLinks = new ArrayList<>();
                } else {
                    table.data[r][c].timeLinks = getLinks(table.data[r][c].text);
                }
            }
        }
        return true;
    }

    public ArrayList<TimeLink> getLinks(String text) {
        ArrayList<TimeLink> links = new ArrayList<>();
        Annotation annotation = new Annotation(text);
        // Document date
//        annotation.set(CoreAnnotations.DocDateAnnotation.class, "2019-01-14");
        pipeline.annotate(annotation);
        for (CoreMap cm : annotation.get(TimeAnnotations.TimexAnnotations.class)) {
            SUTime.Temporal t = cm.get(TimeExpression.Annotation.class).getTemporal();
            if (t.getTime().getTimexType().toString().equals("DATE")) {
                links.add(new TimeLink(cm.toString(), t.toString()));
            }
        }
        return links;
    }

    public static void main(String[] args) {
        System.out.println(new TimeTaggingNode().getLinks("Today is 2019-01-14."));
    }
}

