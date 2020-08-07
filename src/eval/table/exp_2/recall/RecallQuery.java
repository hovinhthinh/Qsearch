package eval.table.exp_2.recall;

import model.quantity.Quantity;
import org.junit.Assert;
import util.FileUtils;

import java.util.ArrayList;

public class RecallQuery {
    public String full;
    public String sourceURL;
    public ArrayList<GroundFact> groundFacts;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(">>>> BEGIN >>>>");
        sb.append("\n").append(full);
        sb.append("\n").append(sourceURL);
        for (GroundFact f : groundFacts) {
            sb.append("\n").append(f.entity).append("\t").append(f.q.toString());
        }
        sb.append("\n").append("<<<< END <<<<");
        return sb.toString();
    }

    public static RecallQuery read(FileUtils.LineStream stream) {
        String line;
        while ((line = stream.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            Assert.assertTrue(line.equals(RecallQueryTemplate.BEGIN_QUERY));

            RecallQuery q = new RecallQuery();
            q.full = stream.readLine().trim();
            q.sourceURL = stream.readLine().trim();

            // ground truth
            q.groundFacts = new ArrayList<>();
            String[] arr;
            while (!(arr = stream.readLine().split("\t"))[0].equals(RecallQueryTemplate.END_QUERY)) {
                GroundFact f = new GroundFact(null, null, arr[0], arr[1]);
                f.q = Quantity.fromQuantityString(arr[1]);
                Assert.assertTrue(f.q != null);
                q.groundFacts.add(f);
            }
            Assert.assertTrue(q.groundFacts.size() > 0);
            return q;
        }
        return null;
    }
}
