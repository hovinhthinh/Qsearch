package pipeline.text;

import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.util.Pair;
import model.text.Paragraph;
import model.text.Sentence;
import model.text.tag.EntityTag;
import model.text.tag.TimeTag;
import nlp.NLP;
import nlp.Static;
import org.junit.Assert;
import util.TParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

// Now using HeidelTime
public class TimeTaggingNode implements TaggingNode {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static void main(String[] args) throws Exception {
        System.out.println(Static.getHeidelTime(false).process("I play it last October 2012."));
    }

    private boolean enabled;
    private boolean processWholeParagraph;

    public TimeTaggingNode() {
        this(true);
    }

    public TimeTaggingNode(boolean enabled) {
        this(enabled, true);
    }

    public TimeTaggingNode(boolean enabled, boolean processWholeParagraph) {
        this.enabled = enabled;
        this.processWholeParagraph = processWholeParagraph;
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
            String heidelTimeOutput;
            String reference_date = (String) paragraph.attributes.get(Paragraph.REFERENCE_DATE_KEY);
            ArrayList<String> sents = null;
            if (processWholeParagraph) {
                if (reference_date == null) {
                    heidelTimeOutput = Static.getHeidelTime(false).process(paragraph.toStringWithNewlineAfterSentences());
                } else {
                    heidelTimeOutput = Static.getHeidelTime(true).process(paragraph.toStringWithNewlineAfterSentences(),
                            DATE_FORMAT.parse(reference_date)).replace("PRESENT_REF", reference_date);
                }
                String parsedResult = TParser.getContent(heidelTimeOutput, "<TimeML>", "</TimeML>");
                sents = new ArrayList<>();
                for (String s : parsedResult.split("[\\r\\n]++")) {
                    s = s.trim();
                    if (!s.isEmpty()) {
                        sents.add(s);
                    }
                }
                if (sents.size() != paragraph.sentences.size()) {
                    throw new Exception(TimeTaggingNode.class.getName() + ": Different numbers of sentences" + "\r\n" + paragraph);
                }
            }
            int cur = paragraph.sentences.size() - 1;
            for (int i = paragraph.sentences.size() - 1; i >= 0; --i) {
                Sentence sent = paragraph.sentences.get(i);
                try {
                    sent.timeTags = new ArrayList<>();
                    if (reference_date != null) {
                        sent.referTime = reference_date;
                    }
                    Assert.assertNotNull(sent.entityTags);
                    String taggedSent = null;
                    if (!processWholeParagraph) {
                        if (reference_date == null) {
                            heidelTimeOutput = Static.getHeidelTime(false).process(sent.toString());
                        } else {
                            heidelTimeOutput = Static.getHeidelTime(true).process(sent.toString(),
                                    DATE_FORMAT.parse(reference_date)).replace("PRESENT_REF", reference_date);
                        }
                        String parsedResult = TParser.getContent(heidelTimeOutput, "<TimeML>", "</TimeML>");
                        for (String s : parsedResult.split("[\\r\\n]++")) {
                            s = s.trim();
                            if (!s.isEmpty()) {
                                taggedSent = s;
                            }
                        }
                    } else {
                        taggedSent = sents.get(cur--);
                    }
                    int numPassedTokens = 0, lastProcessedPos = 0;
                    do {
                        int tagStart = taggedSent.indexOf("<TIMEX3 ", lastProcessedPos);
                        if (tagStart == -1) {
                            break;
                        }

                        String fastForwarded = taggedSent.substring(lastProcessedPos, tagStart).trim();
                        numPassedTokens += fastForwarded.isEmpty() ? 0 : NLP.splitSentence(fastForwarded).size();

                        lastProcessedPos = taggedSent.indexOf("</TIMEX3>", tagStart) + "</TIMEX3>".length();
                        Timex time = Timex.fromXml(taggedSent.substring(tagStart, lastProcessedPos));
                        int numInnerTokens = NLP.splitSentence(time.text()).size();
                        if (time.timexType().equals("DATE")) {
                            Pair<Calendar, Calendar> timeRange = null;
                            //
                            try {
                                timeRange = time.getRange();
                            } catch (Exception e) {
                            }
                            // Check if there is no overlap entity tag.
                            boolean flag = true;
                            for (EntityTag et : sent.entityTags) {
                                if (et.beginIndex < numPassedTokens + numInnerTokens && numPassedTokens < et.endIndex) {
                                    flag = false;
                                    break;
                                }
                            }
                            if (flag) {
                                if (timeRange != null) {
                                    sent.timeTags.add(new TimeTag(numPassedTokens, numPassedTokens + numInnerTokens,
                                            timeRange.first.getTimeInMillis(), timeRange.second.getTimeInMillis()));
                                } else {
                                    sent.timeTags.add(new TimeTag(numPassedTokens, numPassedTokens + numInnerTokens, true));
                                }
                            }
                        }
                        numPassedTokens += numInnerTokens;
                    } while (true);
                    String remaining = taggedSent.substring(lastProcessedPos).trim();
                    numPassedTokens += remaining.isEmpty() ? 0 : NLP.splitSentence(remaining).size();

                    if (numPassedTokens != sent.tokens.size()) {
                        // Ensure this sentence will be filtered later on.
                        sent.entityTags.clear();
                        sent.timeTags.clear();
                        throw new Exception(TimeTaggingNode.class.getName() + ": Different numbers of tokens" + "\r\n"
                                + sent + "\r\n" + taggedSent);
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
