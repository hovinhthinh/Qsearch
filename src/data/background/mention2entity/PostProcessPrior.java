package data.background.mention2entity;

import nlp.NLP;
import util.FileUtils;

import java.io.PrintWriter;

@Deprecated
public class PostProcessPrior {
    // <input> <output>
    public static void main(String[] args) {
        PrintWriter out = FileUtils.getPrintWriter(args[1], "UTF-8");
        for (String line : FileUtils.getLineStream(args[0], "UTF-8")) {
            String[] arr = line.split("\t");
            try {
                arr[0] = String.join(" ", NLP.tokenize(arr[0]));
            } catch (Exception e) {
            }
            out.println(arr[0] + "\t" + arr[1]);
        }
        out.close();
    }
}
