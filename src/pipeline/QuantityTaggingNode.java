package pipeline;

import catalog.Unit;
import catalog.UnitPair;
import edu.illinois.cs.cogcomp.quant.driver.QuantSpan;
import edu.illinois.cs.cogcomp.quant.standardize.Quantity;
import iitb.shared.EntryWithScore;
import model.table.Cell;
import model.table.Table;
import model.table.link.QuantityLink;
import nlp.NLP;
import nlp.Static;
import parser.ParseState;
import parser.UnitFeatures;
import parser.UnitSpan;
import util.Quadruple;

import java.util.ArrayList;
import java.util.List;

// Now using IllinoisQuantifier.
// TODO: Problem: "Google pays $ 17 million compensation over privacy breach .": compensation is detected in the
// quantity span.
// TODO: Problem: "Paris still has more than 2,000 troops deployed in Mali .": troops are in both quantity and context.
// TODO: Completed in 2010 , the Zifeng Tower in Nanjing has an architectural height of 1,476 feet ( 450 meters ) and is occupied to a height of 1,039 feet ( 316.6 meters ) .
// TODO: In 2013 , SBB Cargo had 3,061 employees and achieved consolidated sales of CHF 953 million .
public class QuantityTaggingNode implements TaggingNode {

    // returns: Unit, Multiplier, Span String, Preprocessed String (contains Span String)
    private static Quadruple<Unit, Double, String, String> getHeaderUnit(String header) {
        try {
            ParseState[] state = new ParseState[1];
            List<EntryWithScore<Unit>> units = (List<EntryWithScore<Unit>>) Static.getTableHeaderParser().parseHeaderExplain(header, null, 0, state);
            if (units == null || units.size() == 0) {
                return null;
            }
            EntryWithScore<Unit> eu = units.get(0);
            String span = null;
            if (eu instanceof UnitFeatures) {
                UnitFeatures uf = (UnitFeatures) eu;
                span = String.join(" ", state[0].tokens.subList(uf.start(), uf.end() + 1));
            } else if (eu instanceof UnitSpan) {
                UnitSpan us = (UnitSpan) eu;
                span = String.join(" ", state[0].tokens.subList(us.start(), us.end() + 1));
            } else {
                throw new Exception("Invalid entry unit");
            }
            Unit u = eu.getKey();
            if (!(u instanceof UnitPair)) {
                if (u.getParentQuantity().isUnitLess()) {
                    return new Quadruple(null, u.getMultiplier(), span, String.join(" ", state[0].tokens));
                } else {
                    return new Quadruple(u, 1, span, String.join(" ", state[0].tokens));
                }
            } else {
                UnitPair up = (UnitPair) u;
                if (up.getOpType() != UnitPair.OpType.Mult) {
                    // only support Mult: e.g. million usd
                    // Alt: usd | euro (not supported)
                    // Ratio: usd / year (supported by native units from QuTree).
                    return null;
                }
                Unit u1 = up.getUnit(0);
                if (u1 instanceof UnitPair) {
                    return null;
                }
                Unit u2 = up.getUnit(1);
                return new Quadruple(u1, u2.getMultiplier(), span, String.join(" ", state[0].tokens));
            }
        } catch (Exception e) {
            e.printStackTrace();
//            throw new RuntimeException(e);
            return null;
        }
    }

    public static void main(String[] args) {
        String sentStr = "Neymar to earn $ 916k a week after record transfer .";
        for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(sentStr, true)) {
            if (span.object instanceof Quantity) {
                Quantity q = (Quantity) span.object;
                System.out.println(span.toString());
                System.out.println(q.phrase);
            }
        }

        System.out.println(getHeaderUnit("value ( billion usd )"));
        System.out.println(getHeaderUnit("speed ( km / h )"));
        System.out.println(getHeaderUnit("speed ( M km / h )"));
        System.out.println(getHeaderUnit("value (million euro )").first.getConversionFactor());
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
