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
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

// Now using IllinoisQuantifier.
// TODO: Problem: "Google pays $ 17 million compensation over privacy breach .": compensation is detected in the
// quantity span.
// TODO: Problem: "Paris still has more than 2,000 troops deployed in Mali .": troops are in both quantity and context.
// TODO: Completed in 2010 , the Zifeng Tower in Nanjing has an architectural height of 1,476 feet ( 450 meters ) and is occupied to a height of 1,039 feet ( 316.6 meters ) .
// TODO: In 2013 , SBB Cargo had 3,061 employees and achieved consolidated sales of CHF 953 million .
public class QuantityTaggingNode implements TaggingNode {
    public static final double CLEAR_TIME_LINK_THRESHOLD = 0.5;

    // returns: Unit (QuTree Basename), Multiplier, Span String, Preprocessed String (removed Span String)
    public static Quadruple<String, Double, String, String> getHeaderUnit(String header) {
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

        Cell cell = new Cell();
        cell.timeLinks = new ArrayList<>();
        cell.text = "455 ft ( 138.6 m )";
        new QuantityTaggingNode().tagBodyCell(cell, null, 1, "dummy header", false);
        for (QuantityLink l : cell.quantityLinks) {
            System.out.println(l.quantity.toString() + "\t" + l.text);
        }
        System.out.println(cell.getRepresentativeQuantityLink());
    }

    private Pattern timeMatcher = Pattern.compile("\\d{1,2}:\\d{2}[\\.:]\\d{2}"); // e.g., 3:50.77 or 1:15:20

    private void tagBodyCell(Cell cell, String unit, double multiplier, String originalCombinedHeader, boolean hasTimeLink) {
        if (cell.quantityLinks == null) {
            cell.quantityLinks = new ArrayList<>();
        }
        if (cell.entityLinks != null && cell.entityLinks.size() > 0) {
            // not tagging cells with entities;
            // only in case of wikipedia, quantity tagging is run after entities tagging.
            //
            // for webtables, quantities tagging nodes should be done before entities tagging node.
            return;
        }
        if (hasTimeLink != (cell.getRepresentativeTimeLink() != null)) {
            return;
        }

        // Use rules first
        if (timeMatcher.matcher(cell.text).matches()) {
            int p1 = cell.text.indexOf(':');
            int p2 = cell.text.lastIndexOf('.');
            if (p2 != -1) {
                // min:sec:centisec
                Integer a = Integer.parseInt(cell.text.substring(0, p1));
                Integer b = Integer.parseInt(cell.text.substring(p1 + 1, p2));
                Integer c = Integer.parseInt(cell.text.substring(p2 + 1));
                if (a > 0 && a < 60 && b < 60 && c < 100) {
                    cell.quantityLinks.add(
                            new QuantityLink(cell.text, a * 60 + b + 0.01 * c, "second", "="));
                    return;
                }
            } else {
                // hour:min:sec
                p2 = cell.text.lastIndexOf(':');
                Integer a = Integer.parseInt(cell.text.substring(0, p1));
                Integer b = Integer.parseInt(cell.text.substring(p1 + 1, p2));
                Integer c = Integer.parseInt(cell.text.substring(p2 + 1));
                if (a > 0 && b < 60 && c < 60) {
                    cell.quantityLinks.add(
                            new QuantityLink(cell.text, a * 3600 + b * 60 + c, "second", "="));
                    return;
                }
            }
        }
        // Now use IllinoisQuantifier
        String dumpyText = "This quantity is " + cell.text + " .";
        boolean firstQuantity = true;
        for (QuantSpan span : Static.getIllinoisQuantifier().getSpans(dumpyText, true)) {
            if (span.object instanceof Quantity) {
                Quantity q = (Quantity) span.object;

                // In case the unit is extracted from header, the value from cell should be divided by 100.
                // This is due to the reason that Illinois Quantifier return percentage between [0,1]
                if (unit != null && unit.equals("percent") && q.value > 1.0) {
                    multiplier /= 100;
                }

                q.units = NLP.stripSentence(q.units);
                if (q.units.equalsIgnoreCase("millions")) {
                    q.value *= 1000000;
                    q.units = "";
                }

                // prefer header unit if available (only for firstQuantity)
                String cellUnit = firstQuantity ? NLP.stripSentence(unit != null ? unit : q.units) : q.units;
                firstQuantity = false;

                String quantitySpan = dumpyText.substring(span.start, span.end);

                // !!! Apply rules here (when unit is missing)
                if (cellUnit.trim().isEmpty()) {
                    if (originalCombinedHeader.equalsIgnoreCase("height")) {
                        // human height
                        if (q.value > 100 && q.value <= 250) {
                            cellUnit = "centimetre";
                        } else if (q.value > 1 && q.value <= 2.5) {
                            cellUnit = "metre";
                        }
                    } else if (originalCombinedHeader.equalsIgnoreCase("reaction")) {
                        // athletics reaction time
                        if (q.value > 0 && q.value < 0.5) {
                            cellUnit = "second";
                        }
                    } else if (originalCombinedHeader.equalsIgnoreCase("weight")) {
                        // human weight
                        if (q.value >= 50 && q.value <= 100) {
                            cellUnit = "kilogram";
                        }
                    } else if (originalCombinedHeader.equalsIgnoreCase("record")
                            || originalCombinedHeader.equalsIgnoreCase("time")) {
                        if (q.value < 60) {
                            cellUnit = "second";
                        }
                    } else if (cell.text.contains("€" + quantitySpan)) {
                        cellUnit = "euro";
                        quantitySpan = "€" + quantitySpan;
                    } else if (cell.text.contains("€ " + quantitySpan)) {
                        cellUnit = "euro";
                        quantitySpan = "€ " + quantitySpan;
                    } else if (cell.text.contains("£" + quantitySpan)) {
                        cellUnit = "british pound";
                        quantitySpan = "£" + quantitySpan;
                    } else if (cell.text.contains("£ " + quantitySpan)) {
                        cellUnit = "british pound";
                        quantitySpan = "£ " + quantitySpan;
                    } else if (cell.text.contains("¥" + quantitySpan)) {
                        cellUnit = "yuan";
                        quantitySpan = "¥" + quantitySpan;
                    } else if (cell.text.contains("¥ " + quantitySpan)) {
                        cellUnit = "yuan";
                        quantitySpan = "¥ " + quantitySpan;
                    }
                }

                cell.quantityLinks.add(new QuantityLink(quantitySpan, q.value * multiplier, cellUnit, q.bound));
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
            String header = table.getOriginalCombinedHeader(col);

            // Apply rules to get header unit:
            Quadruple<String, Double, String, String> unitInfoFromHeader = null;
            // these are cases when 2 units are given (we only consider the first one, the second one is a converted value of another unit)
            for (String suffix : new String[]{
                    " mph ( km / h )",
                    " km / h ( mph )",
                    " Miles ( Km )",
                    " mi ( km )",
                    " inches ( cm )",
                    " m ( ft )",
                    " meters ( feet )",
                    " ft ( m )",
                    " ft (m)",
                    " m² ( sq ft ) )",
                    " km² ( sq mi )",
                    " km 2 ( sq mi )"}) {
                if (header.endsWith(suffix)) {
                    unitInfoFromHeader = new Quadruple<>();
                    // main unit
                    unitInfoFromHeader.first = suffix.substring(0, suffix.indexOf('(')).trim();
                    // muliplier
                    unitInfoFromHeader.second = 1.0;
                    // unit span
                    unitInfoFromHeader.third = suffix.trim();
                    // combined header after removing unit span
                    unitInfoFromHeader.fourth = header.substring(0, header.length() - suffix.length()).trim();
                    break;
                }
            }

            // Now get header unit using QWET.
            if (unitInfoFromHeader == null) {
                unitInfoFromHeader = getHeaderUnit(header);
            }


            if (unitInfoFromHeader != null) {
                table.setHeaderUnitSpan(col, unitInfoFromHeader.third);
                // Remove unit span from combined header
                table.setCombinedHeader(col, unitInfoFromHeader.fourth);
            }

            int nQLs = 0;
            for (Cell[] row : table.data) {
                tagBodyCell(row[col], unitInfoFromHeader == null ? null : unitInfoFromHeader.first,
                        unitInfoFromHeader == null ? 1.0 : unitInfoFromHeader.second, header, false);
                if (row[col].getRepresentativeQuantityLink() != null) {
                    ++nQLs;
                }
            }
            if (nQLs >= CLEAR_TIME_LINK_THRESHOLD * table.nDataRow) {
                for (Cell[] row : table.data) {
                    row[col].resetCachedRepresentativeLink();
                    tagBodyCell(row[col], unitInfoFromHeader == null ? null : unitInfoFromHeader.first,
                            unitInfoFromHeader == null ? 1.0 : unitInfoFromHeader.second, header, true);
                    if (row[col].getRepresentativeQuantityLink() != null) {
                        row[col].timeLinks.clear();
                        row[col].resetCachedRepresentativeLink();
                    }
                }
            }

            HashMap<String, Integer> unitToFreq = new HashMap<>();
            for (Cell[] row : table.data) {
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
