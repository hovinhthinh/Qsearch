package pipeline;

import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.standardize.Quantity;
import model.table.Table;
import model.text.Sentence;
import model.text.tag.QuantityTag;
import model.text.tag.Tag;
import nlp.NLP;
import nlp.Static;

import java.util.ArrayList;

// Now using IllinoisQuantifier.
// TODO: Problem: "Google pays $ 17 million compensation over privacy breach .": compensation is detected in the
// quantity span.
// TODO: Problem: "Paris still has more than 2,000 troops deployed in Mali .": troops are in both quantity and context.
// TODO: Completed in 2010 , the Zifeng Tower in Nanjing has an architectural height of 1,476 feet ( 450 meters ) and is occupied to a height of 1,039 feet ( 316.6 meters ) .
// TODO: In 2013 , SBB Cargo had 3,061 employees and achieved consolidated sales of CHF 953 million .

@Deprecated
public class QuantityTaggingNode implements TaggingNode {
    public static void main(String[] args) {
        String sentStr = "Neymar to earn $ 916k a week after record transfer .";
        for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(sentStr, true)) {
            if (span.object instanceof Quantity) {
                Quantity q = (Quantity) span.object;
                System.out.println(span.toString());
                System.out.println(q.phrase);
            }
        }
    }

    private void tagSentence(Sentence sent) {
        sent.quantityTags = new ArrayList<>();
        String sentStr = sent.toString();
        for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(sentStr, true)) {
            if (span.object instanceof Quantity) {
                String qStr = sentStr.substring(span.start, span.end).trim();
                String passed = sentStr.substring(0, span.start).trim();
                int startToken = 0;
                if (!passed.isEmpty()) {
                    startToken = NLP.splitSentence(passed).size();
                    // FIX_FOR:"NFor further information , please contact : Virtue PR & Marketing Communications P.O
                    // Box : 191931 Dubai , United Arab Emirates Tel : +97144508835"
                    if (sentStr.charAt(span.start) != ' ' && sentStr.charAt(span.start - 1) != ' ') {
                        --startToken;
                    }
                }
                int endToken = startToken + NLP.splitSentence(qStr).size();
                Quantity q = (Quantity) span.object;
                // Check if there is no overlap tag.
                boolean flag = true;
                loop:
                for (ArrayList tagList : new ArrayList[]{sent.entityTags, sent.timeTags}) {
                    for (Object o : tagList) {
                        Tag et = (Tag) o;
                        if (et.beginIndex < endToken && startToken < et.endIndex) {
                            flag = false;
                            break loop;
                        }
                    }
                }
                if (flag) {
                    sent.quantityTags.add(
                            new QuantityTag(startToken, endToken, q.value, NLP.stripSentence(q.units), q.bound));
                }
            }
        }
    }

    @Override
    public boolean process(Table table) {
//        for (Sentence sent : table.sentences) {
//            tagSentence(sent);
//        }
        return false;
    }
}
