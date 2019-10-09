import catalog.Unit;
import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;
import parser.CFGParser4Header;
import parser.ParseState;
import parser.UnitSpan;

import java.io.FileReader;
import java.util.List;

public class TestUnitTagger {
    public static void main(String[] args) throws Exception {

        CFGParser4Header parser = new CFGParser4Header(XMLConfigs.load(new FileReader("conf/unit-tagger-configs.xml")));
        List<EntryWithScore<Unit>> unitsR = (List<EntryWithScore<Unit>>) parser.parseHeaderExplain("Net worth in dollars per year", null, 1, new ParseState[1]);
        System.out.println("====================================================================================================");
        if (unitsR != null) {
            for (EntryWithScore<Unit> x : unitsR) {
                UnitSpan u = (UnitSpan) x;
                System.out.println(u.getScore());
                System.out.println(u.start() + " " + u.end());
                System.out.println(u.toString());
                System.out.println(u.getKey().getName());
                System.out.println(u.getKey().getBaseName());
                System.out.println(u.getKey().getSymbol());
            }
        }
    }
}
