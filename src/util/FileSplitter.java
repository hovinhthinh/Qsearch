package util;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;

public class FileSplitter {
    // Args: <input> <nParts> [outputFolder]
    // Output files: <input.slices>/part0.gz, <input.slices>/part1.gz,..., <input.slices>/part<nPart-1>.gz; with GZIP compression.
    //
    // or:
    // <input> <output1> <ratio1> <output2> <ratio2> ... <outputn>
    // ratio* should sum up to 1. <outputn> does not need the ratio, which will be calculated automatically.
    // <input> will be shuffled before splitting.
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Invalid arguments.");
        } else if (args.length <= 3) {
            File outputFolder = (args.length == 3) ? new File(args[2]) : new File(args[0] + ".slices");
            outputFolder.mkdir();

            PrintWriter[] outs = new PrintWriter[Integer.parseInt(args[1])];
            for (int i = 0; i < outs.length; ++i) {
                outs[i] = FileUtils.getPrintWriter(new File(outputFolder, String.format("part%d.gz", i)), Charset.forName("UTF-8"));
            }

            SelfMonitor m = new SelfMonitor(FileSplitter.class.getName() + " -- " + new File(args[0]).getName(), -1, 10);
            m.start();
            if (args[0].endsWith(".tar.bz2")) { // Each entry is a file now.
                TarArchiveInputStream tarInput =
                        new TarArchiveInputStream(new BZip2CompressorInputStream(new FileInputStream(args[0])));
                int cur = 0;
                while ((tarInput.getNextTarEntry()) != null) {
                    for (String line : new FileUtils.LineStream(tarInput, Charset.forName("UTF-8"), false)) {
                        outs[cur++].println(line);
                        if (cur == outs.length) {
                            cur = 0;
                        }
                        m.incAndGet();
                    }
                }
                tarInput.close();
            } else {
                int cur = 0;
                for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
                    outs[cur++].println(line);
                    if (cur == outs.length) {
                        cur = 0;
                    }
                    m.incAndGet();
                }
            }
            for (PrintWriter w : outs) {
                w.close();
            }
            m.forceShutdown();
        } else {
            ArrayList<String> lines = FileUtils.getLines(args[0], "UTF-8");
            Collections.shuffle(lines);
            PrintWriter[] outs = new PrintWriter[args.length / 2];
            int[] counts = new int[args.length / 2];
            for (int i = 0; i < args.length / 2; ++i) {
                outs[i] = FileUtils.getPrintWriter(args[i * 2 + 1], "UTF-8");
                counts[i] = i == (args.length / 2 - 1) ? -1 :
                        (int) (Double.parseDouble(args[i * 2 + 2]) * lines.size());
            }
            int cur = 0;
            for (String line : lines) {
                while (counts[cur] <= 0 && cur < outs.length - 1) {
                    ++cur;
                }
                outs[cur].println(line);
                --counts[cur];
            }
            for (PrintWriter o : outs) {
                o.close();
            }
        }
    }
}
