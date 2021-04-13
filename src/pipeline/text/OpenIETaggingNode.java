package pipeline.text;

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
                if (ALLOWED_CONTEXT_POSTAGS.contains(t.POS) && !NLP.BLOCKED_STOPWORDS.contains(t.str.toLowerCase())) {
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
        loop:
        for (Instance ins : JavaConversions.seqAsJavaList(Static.getOpenIe().extract(sentStr).toList())) {
            if (ins.confidence() < minimumFactConfidence) {
                continue;
            }
            Extraction e = ins.extraction();

            // Extract raw texts.
            ArrayList<String> rawSubjectTexts = new ArrayList<>();
            ArrayList<String> otherArgsTexts = new ArrayList<>();
            rawSubjectTexts.add(e.arg1().text().trim());
            otherArgsTexts.add(e.rel().text().trim());
            for (Argument a : JavaConversions.seqAsJavaList(e.arg2s())) {
                otherArgsTexts.add(a.text().trim());
            }

            // Reduce to token segments.
            List<Pair<Integer, Integer>> subjectSegments = new ArrayList<>();
            List<Pair<Integer, Integer>> otherArgsSegments = new ArrayList<>();
            for (String s : rawSubjectTexts) {
                if (s.isEmpty()) {
                    continue loop;
                }
                int pstart;
                if (sentStr.startsWith(s)) {
                    pstart = 0;
                } else if (sentStr.endsWith(s)) {
                    pstart = sentStr.length() - s.length();
                } else if ((pstart = sentStr.indexOf(String.format(" %s ", s))) != -1) {
                    ++pstart;
                } else {
                    continue loop;
                }
                String passedStr = sentStr.substring(0, pstart).trim();
                int start = passedStr.isEmpty() ? 0 : NLP.splitSentence(passedStr).size();
                int end = start + NLP.splitSentence(s).size();
                subjectSegments.add(new Pair(start, end));
            }
            for (String s : otherArgsTexts) {
                if (s.isEmpty()) {
                    continue loop;
                }
                int pstart;
                if (sentStr.startsWith(s)) {
                    pstart = 0;
                } else if (sentStr.endsWith(s)) {
                    pstart = sentStr.length() - s.length();
                } else if ((pstart = sentStr.indexOf(String.format(" %s ", s))) != -1) {
                    ++pstart;
                } else {
                    continue loop;
                }
                String passedStr = sentStr.substring(0, pstart).trim();
                int start = passedStr.isEmpty() ? 0 : NLP.splitSentence(passedStr).size();
                int end = start + NLP.splitSentence(s).size();
                otherArgsSegments.add(new Pair(start, end));
            }

            // Extract quantitative facts.
            // There must be exactly 1 EntityTag in the subject segments, no QuantityTag in subject segments,
            // and 1 QuantityTag in other segments, .
            // Everything else could be in the context: either EntityTag, TimeTag, or a normal token.
            Quadruple<List<EntityTag>, List<QuantityTag>, List<TimeTag>, List<Token>> subjectParts
                    = extractFromSegment(sent, subjectSegments);
            Quadruple<List<EntityTag>, List<QuantityTag>, List<TimeTag>, List<Token>> otherParts
                    = extractFromSegment(sent, otherArgsSegments);


            if (subjectParts.first.size() != 1 || subjectParts.second.size() != 0 || otherParts.second.size() != 1) {
                // add negative quantity facts.
                for (QuantityTag nq : new ArrayList<QuantityTag>() {{
                    addAll(subjectParts.second);
                    addAll(otherParts.second);
                }}) {
                    QuantitativeFact nf = new QuantitativeFact();
                    nf.conf = ins.confidence();
                    nf.negated = e.negated();
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
            qfact.negated = e.negated();

            sent.quantitativeFacts.add(qfact);
        }
    }

    @Override
    public boolean process(Paragraph paragraph) {
        for (Sentence sent : paragraph.sentences) {
            try {
                tag(sent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String s;
        System.out.print(">> ");
        while ((s = in.nextLine()) != null) {
            try {
                for (Instance ins : JavaConversions.seqAsJavaList(Static.getOpenIe().extract(s).toList())) {
                    System.out.println("========================================");
                    System.out.println("conf: " + ins.confidence());

                    Extraction e = ins.extraction();
                    System.out.println("negated: " + e.negated());
                    System.out.println("passive: " + e.passive());
                    System.out.println("(S:" + e.arg1().getClass() + "): " + e.arg1().text());
                    System.out.println("(R:" + e.rel().getClass() + "): " + e.rel().text());
                    for (Argument a : JavaConversions.seqAsJavaList(e.arg2s())) {
                        System.out.println("(P:" + a.getClass() + "): " + a.text());
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            System.out.println();
            System.out.print(">> ");
        }
    }
}
