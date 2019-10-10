import catalog.Unit;
import catalog.UnitPair;
import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;
import parser.*;

import java.io.FileReader;
import java.util.List;

public class TestUnitTagger {
    public static void main(String[] args) throws Exception {
        CFGParser4Header parser = new CFGParser4Header(XMLConfigs.load(new FileReader("conf/unit-tagger-configs.xml")));

//        FeatureBasedParser parser = new FeatureBasedParser(XMLConfigs.load(new FileReader("conf/unit-tagger-configs.xml")), null);
        ParseState[] state = new ParseState[1];
        List<EntryWithScore<Unit>> units = (List<EntryWithScore<Unit>>) parser.parseHeaderExplain("Net worth in dollars per year", null, 0, state);
        System.out.println("====================================================================================================");
        if (units != null) {
            System.out.println(units.size());
            for (EntryWithScore<Unit> x : units) {
                System.out.println(state[0].tokens);
                if (x instanceof UnitFeatures) {
                    System.out.println("UnitFeatures");
                    UnitFeatures u = (UnitFeatures) x;
                    System.out.println(u.start() + " " + u.end());
                    System.out.println("Name: " + u.getKey().getName());
                    if (u.getKey() instanceof UnitPair) {
                        for (int i = 0; i < 2; ++i) {
                            Unit k = ((UnitPair) u.getKey()).getUnit(i);
                            System.out.println("Unit " + i + ":");
                            System.out.println("Base Names: " + String.join(" ; ", k.getBaseNames()));
                            System.out.println("Symbol: " + k.getSymbol());
                            System.out.println("Base Symbols: " + String.join(" ; ", k.getBaseSymbols()));
                            System.out.println("Lemmas: " + k.getLemmas());
                            System.out.println("Conversion Factor: " + k.getConversionFactor());
                            System.out.println("Parent Quantity Concept: " + k.getParentQuantity().getConcept());
                        }
                    } else {
                        System.out.println("Base Names: " + String.join(" ; ", u.getKey().getBaseNames()));
                        System.out.println("Symbol: " + u.getKey().getSymbol());
                        System.out.println("Base Symbols: " + String.join(" ; ", u.getKey().getBaseSymbols()));
                        System.out.println("Lemmas: " + u.getKey().getLemmas());
                        System.out.println("Conversion Factor: " + u.getKey().getConversionFactor());
                        System.out.println("Parent Quantity Concept: " + u.getKey().getParentQuantity().getConcept());
                    }

                } else if (x instanceof UnitSpan) {
                    System.out.println("UnitSpan");
                    UnitSpan u = (UnitSpan) x;
                    System.out.println(u.start() + " " + u.end());
                    System.out.println("Name: " + u.getKey().getName());
                    if (u.getKey() instanceof UnitPair) {
                        for (int i = 0; i < 2; ++i) {
                            Unit k = ((UnitPair) u.getKey()).getUnit(i);
                            System.out.println("Unit " + i + ":");
                            System.out.println("Base Names: " + String.join(" ; ", k.getBaseNames()));
                            System.out.println("Symbol: " + k.getSymbol());
                            System.out.println("Base Symbols: " + String.join(" ; ", k.getBaseSymbols()));
                            System.out.println("Lemmas: " + k.getLemmas());
                            System.out.println("Conversion Factor: " + k.getConversionFactor());
                            System.out.println("Parent Quantity Concept: " + k.getParentQuantity().getConcept());
                        }
                    } else {
                        System.out.println("Base Names: " + String.join(" ; ", u.getKey().getBaseNames()));
                        System.out.println("Symbol: " + u.getKey().getSymbol());
                        System.out.println("Base Symbols: " + String.join(" ; ", u.getKey().getBaseSymbols()));
                        System.out.println("Lemmas: " + u.getKey().getLemmas());
                        System.out.println("Conversion Factor: " + u.getKey().getConversionFactor());
                        System.out.println("Parent Quantity Concept: " + u.getKey().getParentQuantity().getConcept());
                    }
                }
            }


        }
        System.out.println("====================================================================================================");
        CFGParser4Text parserBody = new CFGParser4Text(parser.options, parser.quantityDict, parser.conceptClassifier);

        units = (List<EntryWithScore<Unit>>) parserBody.parseCell("$355,905", units, 1, state[0]);
        if (units != null) {
            System.out.println(units.size());
            for (EntryWithScore<Unit> x : units) {
                System.out.println(state[0].tokens);
                if (x instanceof UnitFeatures) {
                    System.out.println("UnitFeatures");
                    UnitFeatures u = (UnitFeatures) x;
                    System.out.println(u.start() + " " + u.end());
                    System.out.println("Name: " + u.getKey().getName());
                    if (u.getKey() instanceof UnitPair) {
                        for (int i = 0; i < 2; ++i) {
                            Unit k = ((UnitPair) u.getKey()).getUnit(i);
                            System.out.println("Unit " + i + ":");
                            System.out.println("Base Names: " + String.join(" ; ", k.getBaseNames()));
                            System.out.println("Symbol: " + k.getSymbol());
                            System.out.println("Base Symbols: " + String.join(" ; ", k.getBaseSymbols()));
                            System.out.println("Lemmas: " + k.getLemmas());
                            System.out.println("Conversion Factor: " + k.getConversionFactor());
                            System.out.println("Parent Quantity Concept: " + k.getParentQuantity().getConcept());
                        }
                    } else {
                        System.out.println("Base Names: " + String.join(" ; ", u.getKey().getBaseNames()));
                        System.out.println("Symbol: " + u.getKey().getSymbol());
                        System.out.println("Base Symbols: " + String.join(" ; ", u.getKey().getBaseSymbols()));
                        System.out.println("Lemmas: " + u.getKey().getLemmas());
                        System.out.println("Conversion Factor: " + u.getKey().getConversionFactor());
                        System.out.println("Parent Quantity Concept: " + u.getKey().getParentQuantity().getConcept());
                    }

                } else if (x instanceof UnitSpan) {
                    System.out.println("UnitSpan");
                    UnitSpan u = (UnitSpan) x;
                    System.out.println(u.start() + " " + u.end());
                    System.out.println("Name: " + u.getKey().getName());
                    if (u.getKey() instanceof UnitPair) {
                        for (int i = 0; i < 2; ++i) {
                            Unit k = ((UnitPair) u.getKey()).getUnit(i);
                            System.out.println("Unit " + i + ":");
                            System.out.println("Base Names: " + String.join(" ; ", k.getBaseNames()));
                            System.out.println("Symbol: " + k.getSymbol());
                            System.out.println("Base Symbols: " + String.join(" ; ", k.getBaseSymbols()));
                            System.out.println("Lemmas: " + k.getLemmas());
                            System.out.println("Conversion Factor: " + k.getConversionFactor());
                            System.out.println("Parent Quantity Concept: " + k.getParentQuantity().getConcept());
                        }
                    } else {
                        System.out.println("Base Names: " + String.join(" ; ", u.getKey().getBaseNames()));
                        System.out.println("Symbol: " + u.getKey().getSymbol());
                        System.out.println("Base Symbols: " + String.join(" ; ", u.getKey().getBaseSymbols()));
                        System.out.println("Lemmas: " + u.getKey().getLemmas());
                        System.out.println("Conversion Factor: " + u.getKey().getConversionFactor());
                        System.out.println("Parent Quantity Concept: " + u.getKey().getParentQuantity().getConcept());
                    }
                }
            }
        }
    }
}
