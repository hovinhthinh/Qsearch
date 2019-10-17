package tmp;

import data.wikipedia.WIKIPEDIA;
import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import util.FileUtils;

import java.io.PrintWriter;

public class TabELEntityLinkStats {
    public static void main(String[] args) {
        int skip = 0;
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        for (String line : stream) {
            Table table = WIKIPEDIA.parseFromJSON(line);
            if (table == null) {
                System.out.println("Skip: " + (++skip));
                continue;
            }
            for (Cell[][] part : new Cell[][][]{table.header, table.data}) {
                for (int i = 0; i < part.length; ++i) {
                    for (int j = 0; j < part[i].length; ++j) {
                        for (EntityLink e : part[i][j].entityLinks) {
                            System.out.println(e.target);
                        }
                    }
                }
            }
        }
        out.close();
    }
}
