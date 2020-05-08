package pipeline.text;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.util.CoreMap;
import model.text.Paragraph;
import model.text.Sentence;
import model.text.tag.EntityTag;
import model.text.tag.TimeTag;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// Now using CoreNLP
public class SUTimeTaggingNode implements TaggingNode {
    private AnnotationPipeline pipeline;
    private boolean enabled;

    public SUTimeTaggingNode() {
        this(true);
    }

    public SUTimeTaggingNode(boolean enabled) {
        this.enabled = enabled;

        Properties props = new Properties();
        pipeline = new AnnotationPipeline();
        pipeline.addAnnotator(new TokenizerAnnotator(false));
        pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        pipeline.addAnnotator(new POSTaggerAnnotator(false));
        pipeline.addAnnotator(new TimeAnnotator("sutime", props));
    }

    public static void main(String[] args) throws Exception {
//        System.out.println(Static.getHeidelTime(false).process("I play it last October 2012."));
        Annotation annotation = new Annotation("I play it last October 2012. I got married this year.");
        new SUTimeTaggingNode().pipeline.annotate(annotation);

        List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
        System.out.println(tokens);
        for (CoreMap cm : annotation.get(TimeAnnotations.TimexAnnotations.class)) {
            System.out.println(cm);
            System.out.println(cm.get(CoreAnnotations.TokenBeginAnnotation.class).intValue());
            System.out.println(cm.get(CoreAnnotations.TokenEndAnnotation.class).intValue());
        }
    }

    @Override
    public boolean process(Paragraph paragraph) {
        if (!enabled) {
            for (Sentence sent : paragraph.sentences) {
                sent.timeTags = new ArrayList<>();
            }
            return true;
        }
        try {
            // not using reference date
//            String reference_date = (String) paragraph.attributes.get(Paragraph.REFERENCE_DATE_KEY);

            for (int i = paragraph.sentences.size() - 1; i >= 0; --i) {
                Sentence sent = paragraph.sentences.get(i);

                try {
                    Assert.assertNotNull(sent.entityTags);
                    sent.timeTags = new ArrayList<>();

                    // process individual sentence only

                    Annotation annotation = new Annotation(sent.toString());
                    pipeline.annotate(annotation);

                    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);

                    loop:
                    for (CoreMap cm : annotation.get(TimeAnnotations.TimexAnnotations.class)) {
                        int begin = cm.get(CoreAnnotations.TokenBeginAnnotation.class).intValue();
                        int end = cm.get(CoreAnnotations.TokenEndAnnotation.class).intValue();
                        if (end > sent.tokens.size()) {
                            continue;
                        }
                        // double check time tags from SUTime vs OpenIE t okens
                        for (int k = begin; k < end; ++k) {
                            if (!tokens.get(k).value().equals(sent.tokens.get(k).str)) {
                                continue loop;
                            }
                        }
                        // Check if there is no overlap entity tag.
                        for (EntityTag et : sent.entityTags) {
                            if (et.beginIndex < end && begin < et.endIndex) {
                                continue loop;
                            }
                        }
                        sent.timeTags.add(new TimeTag(begin, end, -1, -1));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    paragraph.sentences.remove(i);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return paragraph.sentences.size() > 0;
    }
}