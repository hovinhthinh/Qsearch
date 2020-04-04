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

import java.util.*;

// Now using IllinoisQuantifier.
// TODO: Problem: "Google pays $ 17 million compensation over privacy breach .": compensation is detected in the
// quantity span.
// TODO: Problem: "Paris still has more than 2,000 troops deployed in Mali .": troops are in both quantity and context.
// TODO: Completed in 2010 , the Zifeng Tower in Nanjing has an architectural height of 1,476 feet ( 450 meters ) and is occupied to a height of 1,039 feet ( 316.6 meters ) .
// TODO: In 2013 , SBB Cargo had 3,061 employees and achieved consolidated sales of CHF 953 million .
public class QuantityTaggingNode implements TaggingNode {

    // returns: Unit (QuTree Basename), Multiplier, Span String, Preprocessed String (removed Span String)
    private static Quadruple<String, Double, String, String> getHeaderUnit(String header) {
        try {
            ParseState[] state = new ParseState[1];
            List<EntryWithScore<Unit>> units = (List<EntryWithScore<Unit>>) Static.getTableHeaderParser().parseHeaderExplain(header, null, 0, state);
            if (units == null || units.size() == 0) {
                return null;
            }
            EntryWithScore<Unit> eu = units.get(0);
            String span = null;
            String preprocessed = null;
            int start, end;
            if (eu instanceof UnitFeatures) {
                UnitFeatures uf = (UnitFeatures) eu;
                start = uf.start();
                end = uf.end();
            } else if (eu instanceof UnitSpan) {
                UnitSpan us = (UnitSpan) eu;
                start = us.start();
                end = us.end();
            } else {
                throw new Exception("Invalid entry unit");
            }
            span = String.join(" ", state[0].tokens.subList(start, end + 1));
            for (int i = end; i >= start; --i) {
                state[0].tokens.remove(i);
            }
            preprocessed = String.join(" ", state[0].tokens);
            Unit u = eu.getKey();
            if (!(u instanceof UnitPair)) {
                if (u.getParentQuantity().isUnitLess()) {
                    return new Quadruple(null, u.getMultiplier(), span, preprocessed);
                } else {
                    return new Quadruple(u.getBaseName(), 1.0, span, preprocessed);
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
                return new Quadruple(u1.getBaseName(), u2.getMultiplier(), span, preprocessed);
            }
        } catch (Exception e) {
//            e.printStackTrace();
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
    }

    private void tagBodyCell(Cell cell, String unit, double multiplier) {
        cell.quantityLinks = new ArrayList<>();
        if (cell.entityLinks != null && cell.entityLinks.size() > 0) {
            // not tagging cells with entities;
            // only in case of wikipedia, quantity tagging is run after entities tagging.
            //
            // for webtables, quantities tagging nodes should be done before entities tagging node.
            return;
        }
        if (cell.getRepresentativeTimeLink() != null) {
            return;
        }
        String dumpyText = "This quantity is " + cell.text + " .";
        for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(dumpyText, true)) {
            if (span.object instanceof Quantity) {
                Quantity q = (Quantity) span.object;

                // In case the unit is extracted from header, the value from cell should be divided by 100.
                // This is due to the reason that Illinois Quantifier return percentage between [0,1]
                if (unit != null && unit.equals("percent") && q.value > 1.0) {
                    multiplier /= 100;
                }

                cell.quantityLinks.add(new QuantityLink(dumpyText.substring(span.start, span.end), q.value * multiplier,
                        // prefer header unit if available
                        NLP.stripSentence(unit != null ? unit : q.units), q.bound));
            }
        }
    }

    @Override
    public boolean process(Table table) {
        for (Cell[] row : table.header) {
            for (Cell c : row) {
                c.quantityLinks = new ArrayList<>();
            }
        }

        table.majorUnitInColumn = new String[table.nColumn];
        for (int col = 0; col < table.nColumn; ++col) {
            String header = table.getCombinedHeader(col);
            // Extends units from the header.
            Quadruple<String, Double, String, String> unitInfoFromHeader = getHeaderUnit(header);
            if (unitInfoFromHeader != null) {
                table.setHeaderUnitSpan(col, unitInfoFromHeader.third);
                // Remove unit span from combined header
                table.setCombinedHeader(col, unitInfoFromHeader.fourth);
            }

            HashMap<String, Integer> unitToFreq = new HashMap<>();
            for (Cell[] row : table.data) {
                tagBodyCell(row[col], unitInfoFromHeader == null ? null : unitInfoFromHeader.first,
                        unitInfoFromHeader == null ? 1.0 : unitInfoFromHeader.second);

                QuantityLink q = row[col].getRepresentativeQuantityLink();
                if (q != null) {
                    unitToFreq.put(q.quantity.unit, unitToFreq.getOrDefault(q.quantity.unit, 0) + 1);
                }
            }
            if (unitToFreq.size() > 0) {
                table.majorUnitInColumn[col] = unitToFreq.entrySet().stream().max((a, b) -> (a.getValue().compareTo(b.getValue()))).get().getKey();
            }
        }
        return true;
    }
}
