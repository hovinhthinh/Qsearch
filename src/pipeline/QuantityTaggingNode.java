package pipeline;

import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.standardize.Quantity;
import model.table.Cell;
import model.table.Table;
import model.table.link.QuantityLink;
import nlp.NLP;
import nlp.Static;

import java.util.ArrayList;

// Now using IllinoisQuantifier.
// TODO: Problem: "Google pays $ 17 million compensation over privacy breach .": compensation is detected in the
// quantity span.
// TODO: Problem: "Paris still has more than 2,000 troops deployed in Mali .": troops are in both quantity and context.
// TODO: Completed in 2010 , the Zifeng Tower in Nanjing has an architectural height of 1,476 feet ( 450 meters ) and is occupied to a height of 1,039 feet ( 316.6 meters ) .
// TODO: In 2013 , SBB Cargo had 3,061 employees and achieved consolidated sales of CHF 953 million .
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

    // TODO:
    // - We may need to extends units from the header.
    // - We also may need to add dumpy data: '$916k' - > 'This is $916k.' [DONE]
    private void tagCell(Cell cell) {
        cell.quantityLinks = new ArrayList<>();
        String dumpyText = "This is " + cell.text + " .";
        for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(dumpyText, true)) {
            if (span.object instanceof Quantity) {
                Quantity q = (Quantity) span.object;
                cell.quantityLinks.add(new QuantityLink(dumpyText.substring(span.start, span.end), q.value, NLP.stripSentence(q.units), q.bound));
            }
        }
    }

    @Override
    public boolean process(Table table) {
        for (Cell[] row : table.data) {
            for (Cell c : row) {
                tagCell(c);
            }
        }
        return true;
    }
}
