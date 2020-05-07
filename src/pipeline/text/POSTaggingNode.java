package pipeline.text;

import edu.knowitall.tool.postag.PostaggedToken;
import model.text.Paragraph;
import model.text.Sentence;
import nlp.Static;
import org.junit.Assert;
import scala.collection.JavaConversions;

public class POSTaggingNode implements TaggingNode {
    public static void main(String[] args) {
        String text = "Obama was born in 1961 in Honolulu, Hawaii, two years after the territory was admitted to the " +
                "Union as the 50th state. After graduating from Columbia University in 1983, he worked as a community" +
                " organizer in Chicago.";
        for (PostaggedToken t : JavaConversions.seqAsJavaList(Static.getOpenIe().postagger().postagTokenized(
                Static.getOpenIe().tokenizer().tokenize(text)))) {
            System.out.println(t.string() + " " + t.postag());
        }
    }

    @Override
    public boolean process(Paragraph paragraph) {
        Assert.assertNotNull(paragraph.sentences);
        for (Sentence sent : paragraph.sentences) {
            int cur = 0;
            for (PostaggedToken t : JavaConversions.seqAsJavaList(Static.getOpenIe().postagger().postagTokenized(
                    Static.getOpenIe().tokenizer().tokenize(sent.toString())))) {
                sent.tokens.get(cur++).POS = t.postag();
            }
        }
        return true;
    }
}
