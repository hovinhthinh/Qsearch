package pipeline.text;

import edu.knowitall.collection.immutable.Interval;
import edu.knowitall.openie.Argument;
import edu.knowitall.openie.Extraction;
import edu.knowitall.openie.Instance;
import model.text.Paragraph;
import model.text.QuantitativeFact;
import model.text.Sentence;
import model.text.Token;
import model.text.tag.EntityTag;
import model.text.tag.QuantityTag;
import model.text.tag.TimeTag;
import nlp.NLP;
import nlp.Static;
import scala.collection.JavaConversions;
import util.Pair;
import util.Quadruple;

import java.util.*;

public class OpenIETaggingNode implements TaggingNode {
    public static final Set<String> BLOCKED_STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't", "as"
            , "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can't",
            "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during"
            , "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't", "have", "haven't", "having",
            "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself", "him", "himself", "his", "how",
            "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's", "its",
            "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no", "nor", "not", "of", "off", "on",
            "once", "only", "or", "other", "ought", "our", "ours	", "ourselves", "out", "over", "own", "same",
            "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such", "than", "that",
            "that's", "the", "their", "theirs", "them", "themselves", "then", "there", "there's", "these", "they",
            "they'd", "they'll", "they're", "they've", "this", "those", "through", "to", "too", "under", "until", "up"
            , "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's",
            "when", "when's", "where", "where's", "which", "while", "who", "who's", "whom", "why", "why's", "with",
            "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself",
            "yourselves"
    ));
    private static final Set<String> ALLOWED_CONTEXT_POSTAGS = new HashSet<>(Arrays.asList(
            "FW",
            "JJ", "JJR", "JJS",
            "RB", "RBR", "RBS",
            "NN", "NNS", "NNP", "NNPS",
            "VB", "VBZ", "VBP", "VBD", "VBN", "VBG"
    ));
    // This is number of reduced tokens, i.e. after combining all tokens of the same time/entity into one.
    // Only count for the tokens in the context connecting entity and quantity.
    private int maximumNumberOfFactContextTokens;

    private double minimumFactConfidence;

    public OpenIETaggingNode(int maximumNumberOfFactContextTokens, double minimumFactConfidence) {
        this.maximumNumberOfFactContextTokens = maximumNumberOfFactContextTokens;
        this.minimumFactConfidence = minimumFactConfidence;
    }

    private Quadruple<List<EntityTag>, List<QuantityTag>, List<TimeTag>, List<Token>> extractFromSegment
            (Sentence sent, List<Pair<Integer, Integer>> segments) {

        List<EntityTag> etags = new ArrayList<>();
        List<QuantityTag> qtags = new ArrayList<>();
        List<TimeTag> ttags = new ArrayList<>();
        List<Token> ts = new ArrayList<>();

        for (Pair<Integer, Integer> segment : segments) {
            Set<Integer> remainingTokens = new HashSet<>();
            for (int i = segment.first; i < segment.second; ++i) {
                remainingTokens.add(i);
            }

            for (EntityTag t : sent.entityTags) {
                if (t.beginIndex >= segment.first && t.endIndex <= segment.second) {
                    etags.add(t);
                    for (int i = t.beginIndex; i < t.endIndex; ++i) {
                        remainingTokens.remove(i);
                    }
                }
            }
            for (QuantityTag t : sent.quantityTags) {
                if (t.beginIndex >= segment.first && t.endIndex <= segment.second) {
                    qtags.add(t);
                    for (int i = t.beginIndex; i < t.endIndex; ++i) {
                        remainingTokens.remove(i);
                    }
                }
            }
            for (TimeTag t : sent.timeTags) {
                if (t.beginIndex >= segment.first && t.endIndex <= segment.second) {
                    ttags.add(t);
                    for (int i = t.beginIndex; i < t.endIndex; ++i) {
                        remainingTokens.remove(i);
                    }
                }
            }
            for (int r : remainingTokens) {
                Token t = sent.tokens.get(r);
                if (ALLOWED_CONTEXT_POSTAGS.contains(t.POS) && !BLOCKED_STOPWORDS.contains(t.str.toLowerCase())) {
                    ts.add(t);
                }
            }
        }
        return new Quadruple<>(etags, qtags, ttags, ts);
    }

    private void tag(Sentence sent) {
        sent.quantitativeFacts = new ArrayList<>();
        sent.negativeQuantitativeFacts = new ArrayList<>();

        String sentStr = sent.toString();
        for (Instance ins : JavaConversions.seqAsJavaList(Static.getOpenIe().extract(sentStr).toList())) {
            if (ins.confidence() < minimumFactConfidence) {
                continue;
            }
            Extraction e = ins.extraction();

            List<Interval> rawSubjectSegments = new ArrayList<>();
            List<Interval> rawOtherArgsSegments = new ArrayList<>();

            // Extract raw segments.
            rawSubjectSegments.addAll(JavaConversions.seqAsJavaList(e.arg1().offsets()));
            rawOtherArgsSegments.addAll(JavaConversions.seqAsJavaList(e.rel().offsets()));
            for (Argument a : JavaConversions.seqAsJavaList(e.arg2s())) {
                rawOtherArgsSegments.addAll(JavaConversions.seqAsJavaList(a.offsets()));
            }

            // Reduce to token segments.
            List<Pair<Integer, Integer>> reducedSubjectSegments = new ArrayList<>();
            List<Pair<Integer, Integer>> reducedOtherArgsSegments = new ArrayList<>();
            for (Interval i : rawSubjectSegments) {
                String passedStr = sentStr.substring(0, i.start()).trim();
                int start = passedStr.isEmpty() ? 0 : NLP.splitSentence(passedStr).size();
                int end = start + NLP.splitSentence(sentStr.substring(i.start(), i.end())).size();
                reducedSubjectSegments.add(new Pair(start, end));
            }
            for (Interval i : rawOtherArgsSegments) {
                String passedStr = sentStr.substring(0, i.start()).trim();
                int start = passedStr.isEmpty() ? 0 : NLP.splitSentence(passedStr).size();
                int end = start + NLP.splitSentence(sentStr.substring(i.start(), i.end())).size();
                reducedOtherArgsSegments.add(new Pair(start, end));
            }

            // Extract quantitative facts.
            // There must be exactly 1 EntityTag in the subject segments, no QuantityTag in subject segments,
            // and 1 QuantityTag in other segments, .
            // Everything else could be in the context: either EntityTag, TimeTag, or a normal token.
            Quadruple<List<EntityTag>, List<QuantityTag>, List<TimeTag>, List<Token>> subjectParts
                    = extractFromSegment(sent, reducedSubjectSegments);
            Quadruple<List<EntityTag>, List<QuantityTag>, List<TimeTag>, List<Token>> otherParts
                    = extractFromSegment(sent, reducedOtherArgsSegments);


            if (subjectParts.first.size() != 1 || subjectParts.second.size() != 0 || otherParts.second.size() != 1) {
                // add negative quantity facts.
                for (QuantityTag nq : new ArrayList<QuantityTag>() {{
                    addAll(subjectParts.second);
                    addAll(otherParts.second);
                }}) {
                    QuantitativeFact nf = new QuantitativeFact();
                    nf.conf = ins.confidence();
                    nf.quantityTag = nq;
                    sent.negativeQuantitativeFacts.add(nf);
                }
                continue;
            }


            if (subjectParts.fourth.size() != 0) {
                // TODO: we may allow context tokens in the subject part by disabling this check.
                // continue;
            }

            QuantitativeFact qfact = new QuantitativeFact();
            qfact.entityTag = subjectParts.first.get(0);
            qfact.quantityTag = otherParts.second.get(0);
            qfact.contextEntityTags.addAll(otherParts.first);
            qfact.contextTimeTags.addAll(subjectParts.third);
            qfact.contextTimeTags.addAll(otherParts.third);
            qfact.contextTokens.addAll(subjectParts.fourth);
            qfact.contextTokens.addAll(otherParts.fourth);

            if (qfact.getContextLength() > maximumNumberOfFactContextTokens) {
                continue;
            }
            qfact.conf = ins.confidence();

            sent.quantitativeFacts.add(qfact);
        }
    }

    @Override
    public boolean process(Paragraph paragraph) {
        for (Sentence sent : paragraph.sentences) {
            tag(sent);
        }
        return true;
    }
}
