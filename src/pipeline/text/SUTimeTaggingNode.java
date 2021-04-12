package pipeline.text;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.time.SUTime;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.time.TimeExpression;
import edu.stanford.nlp.util.CoreMap;
import model.text.Paragraph;
import model.text.Sentence;
import model.text.tag.EntityTag;
import model.text.tag.TimeTag;
import nlp.NLP;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// Now using CoreNLP
// FIXME: SUTime only supports resolving RelativeTime from reference time, not from preceding sentences.
// So, param 'processWholeParagraph' has no effect now.
public class SUTimeTaggingNode implements TaggingNode {
    private AnnotationPipeline pipeline;
    private boolean enabled;
    private boolean processWholeParagraph;

    public SUTimeTaggingNode() {
        this(true);
    }

    public SUTimeTaggingNode(boolean enabled) {
        this(enabled, false);
    }

    public SUTimeTaggingNode(boolean enabled, boolean processWholeParagraph) {
        this.enabled = enabled;
        this.processWholeParagraph = processWholeParagraph;

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
            String reference_date = (String) paragraph.attributes.get(Paragraph.REFERENCE_DATE_KEY);

            for (Sentence sent : paragraph.sentences) {
                sent.timeTags = new ArrayList<>();
            }

            if (processWholeParagraph) {
                // Build paragraph string
                String[] sents = new String[paragraph.sentences.size()];
                StringBuilder parStr = new StringBuilder();
                for (int i = 0; i < paragraph.sentences.size(); ++i) {
                    sents[i] = paragraph.sentences.get(i).toString();
                    if (parStr.length() > 0) {
                        parStr.append(" ");
                    }
                    parStr.append(sents[i]);
                }

                Annotation annotation = new Annotation(parStr.toString());
                if (reference_date != null) {
                    annotation.set(CoreAnnotations.DocDateAnnotation.class, reference_date);
                }
                pipeline.annotate(annotation);

                loop:
                for (CoreMap cm : annotation.get(TimeAnnotations.TimexAnnotations.class)) {
                    try {
                        SUTime.Temporal t = cm.get(TimeExpression.Annotation.class).getTemporal();
                        if (!t.getTimexType().toString().equals("DATE")) {
                            System.out.println(cm);
                            System.out.println(t.getTimexType());
                            System.out.println(t.getTimexValue());
                            continue;
                        }
                        if (!(t instanceof SUTime.PartialTime)) {
                            continue;
                        }

                        int beginCharPos = cm.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class).intValue();
                        int endCharPos = cm.get(CoreAnnotations.CharacterOffsetEndAnnotation.class).intValue();
                        int sentIdx = 0;
                        int sentStartCharIndex = 0;
                        while (beginCharPos >= sentStartCharIndex + sents[sentIdx].length() + 1) {
                            sentStartCharIndex += (sents[sentIdx].length() + 1);
                            ++sentIdx;
                        }

                        String tStr = parStr.substring(beginCharPos, endCharPos).trim();
                        String passed = parStr.substring(sentStartCharIndex, beginCharPos).trim();
                        int beginToken = 0;
                        if (!passed.isEmpty()) {
                            beginToken = NLP.splitSentence(passed).size();
                            // FIX_FOR:"NFor further information , please contact : Virtue PR & Marketing Communications P.O
                            // Box : 191931 Dubai , United Arab Emirates Tel : +97144508835"
                            if (parStr.charAt(beginCharPos) != ' ' && parStr.charAt(beginCharPos - 1) != ' ') {
                                --beginToken;
                            }
                        }
                        int endToken = beginToken + NLP.splitSentence(tStr).size();

                        // Check if there is no overlap entity tag.
                        for (EntityTag et : paragraph.sentences.get(sentIdx).entityTags) {
                            if (et.beginIndex < endToken && beginToken < et.endIndex) {
                                continue loop;
                            }
                        }
                        paragraph.sentences.get(sentIdx).timeTags.add(new TimeTag(beginToken, endToken,
                                t.getRange().beginTime().getJodaTimeInstant().getMillis(),
                                t.getRange().endTime().getJodaTimeInstant().getMillis()));
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            } else {
                // process individual sentence
                for (int i = paragraph.sentences.size() - 1; i >= 0; --i) {
                    Sentence sent = paragraph.sentences.get(i);

                    try {
                        Assert.assertNotNull(sent.entityTags);
                        sent.timeTags = new ArrayList<>();

                        String sentStr = sent.toString();
                        Annotation annotation = new Annotation(sentStr);
                        if (reference_date != null) {
                            annotation.set(CoreAnnotations.DocDateAnnotation.class, reference_date);
                        }
                        pipeline.annotate(annotation);

                        loop:
                        for (CoreMap cm : annotation.get(TimeAnnotations.TimexAnnotations.class)) {
                            SUTime.Temporal t = cm.get(TimeExpression.Annotation.class).getTemporal();
                            if (!t.getTimexType().toString().equals("DATE")) {
                                continue;
                            }
                            if (!(t instanceof SUTime.PartialTime)) {
                                continue;
                            }

                            int beginCharPos = cm.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class).intValue();
                            int endCharPos = cm.get(CoreAnnotations.CharacterOffsetEndAnnotation.class).intValue();
                            String tStr = sentStr.substring(beginCharPos, endCharPos).trim();
                            String passed = sentStr.substring(0, beginCharPos).trim();
                            int beginToken = 0;
                            if (!passed.isEmpty()) {
                                beginToken = NLP.splitSentence(passed).size();
                                // FIX_FOR:"NFor further information , please contact : Virtue PR & Marketing Communications P.O
                                // Box : 191931 Dubai , United Arab Emirates Tel : +97144508835"
                                if (sentStr.charAt(beginCharPos) != ' ' && sentStr.charAt(beginCharPos - 1) != ' ') {
                                    --beginToken;
                                }
                            }
                            int endToken = beginToken + NLP.splitSentence(tStr).size();

                            // Check if there is no overlap entity tag.
                            for (EntityTag et : sent.entityTags) {
                                if (et.beginIndex < endToken && beginToken < et.endIndex) {
                                    continue loop;
                                }
                            }
                            sent.timeTags.add(new TimeTag(beginToken, endToken,
                                    t.getRange().beginTime().getJodaTimeInstant().getMillis(),
                                    t.getRange().endTime().getJodaTimeInstant().getMillis()));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        paragraph.sentences.remove(i);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return paragraph.sentences.size() > 0;
    }
}