package data.manual.t2d;

import com.google.gson.Gson;
import data.manual.TruthTable;
import data.tablem.TABLEM;
import model.table.Table;
import pipeline.*;
import util.FileUtils;

import java.io.PrintWriter;

// TODO: Read ground truth file and run the deep model and
public class Evaluate {
    public static TaggingPipeline getDefaultTaggingPipeline() {
        return TaggingPipeline.getDefaultTaggingPipeline();
    }

    // Just the annotations of entities and quantities, there is no linking.
    public static TaggingPipeline getAnnotationPipeline() {
        return new TaggingPipeline(
                new TablePrefilteringNode(),
                new QuantityTaggingNode(),
                new PriorBasedEntityTaggingNode(),
                new ColumnTypeTaggingNode(),
                new PostFilteringNode()
        );
    }

    // Args: <input> <output>
    public static void main(String[] args) {
//        args = "/GW/D5data-10/hvthinh/BriQ-TableM/health_combined.gz /GW/D5data-11/hvthinh/TABLEM/health_combined.out.gz".split("\\s++");

        TaggingPipeline pipeline = getAnnotationPipeline();
        PrintWriter out = FileUtils.getPrintWriter("./eval/T2D/ground_truth+annotation", "UTF-8");
        FileUtils.LineStream stream = FileUtils.getLineStream("./eval/T2D/ground_truth", "UTF-8");

        Gson gson = new Gson();

        for (String line : stream) {
            TruthTable table = gson.fromJson(line, TruthTable.class);
            if (table == null) {
                continue;
            }
            if (!pipeline.tag(table)) {
                continue;
            }
            out.println(gson.toJson(table));
        }
        out.close();
    }

}
