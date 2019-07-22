package model.text;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.WordToSentenceProcessor;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Paragraph {
    public transient static final String REFERENCE_DATE_KEY = "REFERENCE_DATE"; // must be in "yyyy-MM-dd" format.
    public transient HashMap<String, Object> attributes = new HashMap<>(); // Used for any other purpose.
    public Document document;
    public Sentence header;
    public ArrayList<Sentence> sentences = new ArrayList<>();

    public static Paragraph fromText(String text, String source) {
        Paragraph para = new Paragraph();
        List<CoreLabel> tokens = new ArrayList<>();
        PTBTokenizer<CoreLabel> tokenizer = new PTBTokenizer<>(new StringReader(text), new CoreLabelTokenFactory(), "");
        while (tokenizer.hasNext()) {
            tokens.add(tokenizer.next());
        }
        // Split sentences from tokens.
        List<List<CoreLabel>> sentences = new WordToSentenceProcessor<CoreLabel>().process(tokens);
        // Join back together.
        int start = 0, numSents = 0;
        for (List<CoreLabel> sentence : sentences) {
            int end = sentence.get(sentence.size() - 1).endPosition();
            Sentence sent = Sentence.fromText(text.substring(start, end));
            sent.indexInParagraph = numSents++;
            if (source != null) {
                sent.source = source;
            }
            para.sentences.add(sent);
            start = end;
        }
        return para;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences.size(); ++i) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(sentences.get(i).toString());
        }
        return sb.toString();
    }

    public String toStringWithNewlineAfterSentences() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences.size(); ++i) {
            if (i > 0) {
                sb.append("\r\n");
            }
            sb.append(sentences.get(i).toString());
        }
        return sb.toString();
    }
}
