package tmp;

import data.table.wikipedia.WIKIPEDIA;
import model.table.Cell;
import model.table.Table;
import model.table.link.EntityLink;
import util.FileUtils;
import util.Monitor;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

public class TabELEntityLinkStats {
    public static void main(String[] args) {
        int skip = 0;
        final AtomicInteger nLine = new AtomicInteger(0);

        Monitor monitor = new Monitor("TabELEntityLinkStats", -1, 10, null) {
            @Override
            public int getCurrent() {
                return nLine.get();
            }
        };
        monitor.start();
        FileUtils.LineStream stream = FileUtils.getLineStream(args[0], "UTF-8");
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        for (String line : stream) {
            nLine.incrementAndGet();
            Table table = WIKIPEDIA.parseFromJSON(line);
            if (table == null) {
                System.out.println("Skip: " + (++skip));
                continue;
            }
            for (Cell[][] part : new Cell[][][]{table.header, table.data}) {
                for (int i = 0; i < part.length; ++i) {
                    for (int j = 0; j < part[i].length; ++j) {
                        for (EntityLink e : part[i][j].entityLinks) {
                            out.println(e.target);
                        }
                    }
                }
            }
        }
        out.close();
        monitor.forceShutdown();
    }
}
